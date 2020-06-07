package org.matsim.networkReader;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.intersectionSimplifier.DensityCluster;
import org.matsim.core.network.algorithms.intersectionSimplifier.containers.Cluster;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OsmSignalsReader extends SupersonicOsmNetworkReader {

    private static final String[] LANE_ATTRIBUTES = new String[]{
            OsmTags.LANES,
            OsmTags.LANES_FORWARD,
            OsmTags.LANES_BACKWARD,
            OsmTags.TURN_LANES,
            OsmTags.TURN_LANES_FORWARD,
            OsmTags.TURN_LANES_BACKWARD
    };

    // todo use lanes flag
    OsmSignalsReader(OsmSignalsParser parser,
                     BiPredicate<Coord, Integer> includeLinkAtCoordWithHierarchy,
                     AfterLinkCreated afterLinkCreated) {
        super(parser,
                id -> parser.getSignalizedNodes().containsKey(id),
                includeLinkAtCoordWithHierarchy,
                (link, tags, direction) -> {
                    addLinkAttributes(link, tags, direction);
                    afterLinkCreated.accept(link, tags, direction);
                });
    }

    private static void addLinkAttributes(Link link, Map<String, String> tags, Direction direction) {
        addLaneAttributes(link, tags, direction);
        addSignalAttributes(link, tags, direction);
    }

    private static void addLaneAttributes(Link link, Map<String, String> tags, Direction direction) {
        Attributes attributes = link.getAttributes();

        Arrays.stream(LANE_ATTRIBUTES)
                .filter(tags::containsKey)
                .forEach(tag -> attributes.putAttribute(LinkAttributes.OSM_ + tag, tags.get(tag)));

        attributes.putAttribute(LinkAttributes.META_DIRECTION, direction.toString());

        String directedTurnKey = direction == Direction.Reverse ? OsmTags.TURN_LANES_BACKWARD : OsmTags.TURN_LANES_FORWARD;
        String turnKey = tags.containsKey(directedTurnKey) ? directedTurnKey : OsmTags.TURN_LANES;

        if (tags.containsKey(turnKey)) {
            attributes.putAttribute(LinkAttributes.META_LANES, tags.get(turnKey));
        }
    }

    private static void addSignalAttributes(Link link, Map<String, String> tags, Direction direction) {
    }

    private static Map<OsmTurnTagValue, Link> matchInDirectionsToOutLinks(
            Set<OsmTurnTagValue> inDirections,
            NavigableMap<Double, Link> outLinks
    ) {
        return inDirections.stream()
                .collect(Collectors.toMap(Function.identity(), direction ->
                        outLinks.entrySet().stream()
                                .min(Comparator.comparingDouble(angleLink ->
                                        // plus inside abs, because outLink angles are negated
                                        Math.abs(direction.getRadAngle() + angleLink.getKey())))
                                .orElse(outLinks.firstEntry())
                                .getValue()
                ));
    }

    private static String[] getLinkLaneStrs(Link link) {
        String metaLanes = (String) link.getAttributes().getAttribute(LinkAttributes.META_LANES);
        if (metaLanes == null) {
            return new String[0];
        }
        if (metaLanes.endsWith("|")) {
            metaLanes += "none";
        }
        return metaLanes.split("\\|");
    }

    private static EnumSet<OsmTurnTagValue> getLaneDirections(String laneStr) {
        return Arrays.stream(laneStr.split(";"))
                .map(direction -> {
                    try {
                        return OsmTurnTagValue.valueOf(direction.toUpperCase());
                    } catch (IllegalArgumentException ignore) {
                        return OsmTurnTagValue.NONE;
                    }
                }).collect(Collectors.toCollection(() -> EnumSet.noneOf(OsmTurnTagValue.class)));

    }

    //todo collapse lanes with the same directions by incrementing represented lanes attribute
    public Lanes getLanes(Network network) {
        Lanes lanes = LanesUtils.createLanesContainer();

        network.getLinks().values().forEach(link -> {
            String[] laneStrs = getLinkLaneStrs(link);
            if (laneStrs.length == 0) {
                return;
            }

            NavigableMap<Double, Link> outLinks = NetworkUtils.getOutLinksSortedClockwiseByAngle(link);
            if (outLinks.isEmpty()) {
                return;
            }

            Map<Lane, Set<OsmTurnTagValue>> laneToDirections = new HashMap<>();

            LanesToLinkAssignment l2l = lanes.getFactory().createLanesToLinkAssignment(link.getId());
            lanes.addLanesToLinkAssignment(l2l);

            // The indices are used to uniquely discriminate lanes and also double-play as alignments.
            // Indices start from 1, so that the original lane at the start of the link always gets index 0.
            PrimitiveIterator.OfInt laneIndices = IntStream.iterate(1, i -> i + 1).iterator();
            Set<OsmTurnTagValue> allInDirections = Arrays.stream(laneStrs).map(laneStr -> {

                Set<OsmTurnTagValue> laneDirections = getLaneDirections(laneStr);

                int laneIndex = laneIndices.nextInt();

                String laneIdSuffix = laneDirections.stream()
                        .map(OsmTurnTagValue::getLaneIdSuffix)
                        .collect(Collectors.joining("_"));
                Id<Lane> laneId = Id.create(link.getId().toString() + "." + laneIndex + "." + laneIdSuffix, Lane.class);

                Lane lane = lanes.getFactory().createLane(laneId);
                lane.setCapacityVehiclesPerHour(-1.0); // calculated later
                lane.setStartsAtMeterFromLinkEnd(Math.min(link.getLength(), 45.0 /* matsim default */));
                lane.setNumberOfRepresentedLanes(1);
                lane.setAlignment(laneIndex);
                l2l.addLane(lane);

                laneToDirections.put(lane, laneDirections);
                return laneDirections;
            }).flatMap(Collection::stream)
                    .collect(Collectors.toSet());

            Map<OsmTurnTagValue, Link> inDirectionToOutLink = matchInDirectionsToOutLinks(allInDirections, outLinks);

            // below is the debugged code from LanesUtils.createOriginalLanesAndSetLaneCapacities

            Id<Lane> olLaneId = Id.create(link.getId().toString() + ".0.ol", Lane.class);
            Lane olLane = lanes.getFactory().createLane(olLaneId);
            LanesUtils.calculateAndSetCapacity(olLane, false, link, network);
            olLane.setNumberOfRepresentedLanes(link.getNumberOfLanes());
            olLane.setStartsAtMeterFromLinkEnd(link.getLength());

            for (Lane lane : l2l.getLanes().values()) {
                olLane.addToLaneId(lane.getId());
                LanesUtils.calculateAndSetCapacity(lane, true, link, network);
                laneToDirections.get(lane).stream()
                        .map(inDirectionToOutLink::get)
                        .forEach(l -> lane.addToLinkId(l.getId()));
            }

            l2l.addLane(olLane);
        });

        return lanes;
    }

    public SignalsData getSignals(Network network) {
        // filter signal nodes from parser
        List<Node> signals = getSignalizedNodes().entrySet().stream()
                .filter(e -> OsmTags.TRAFFIC_SIGNALS.equals(e.getValue()))
                .map(e -> (Node) network.getNodes().get(Id.createNodeId(e.getKey())))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // cluster signals into signal systems
        double clusteringRadius = 150.0;

        DensityCluster signalSystemClusterer = new DensityCluster(signals, false);
        signalSystemClusterer.clusterInput(clusteringRadius, 1);
        List<Cluster> signalSystems = signalSystemClusterer.getClusterList();

//        Map<Cluster, Set<Node>> intersections = signalSystems.stream()
//                .collect(Collectors.toMap(
//                        Function.identity(),
//                        s -> new HashSet<>(searchableNetwork.getNearestNodes(s.getCenterOfGravity(), clusteringRadius))));
//
//        intersections.forEach((signalSystem, nodes) ->
//                nodes.addAll(signalSystem.getPoints().stream()
//                        .map(ClusterActivity::getNode)
//                        .collect(Collectors.toList())));


//        Map<Integer, ? extends List<? extends Node>> inCounts = network.getNodes().values().stream()
//                .collect(Collectors.groupingBy(n -> n.getInLinks().size()));
//        Map<Integer, ? extends List<? extends Node>> outCounts = network.getNodes().values().stream()
//                .collect(Collectors.groupingBy(n -> n.getOutLinks().size()));


//        long diffs = network.getNodes().values().stream()
//                .filter(n -> {
//                    double inLanes = n.getInLinks().values().stream().mapToDouble(Link::getNumberOfLanes).sum();
//                    double outLanes = n.getOutLinks().values().stream().mapToDouble(Link::getNumberOfLanes).sum();
//                    return inLanes != outLanes;
//                }).count();
//
//        System.out.println(network.getNodes().size());
//        System.out.println(diffs);

//        Map<Double, Integer> laneNumbers = new HashMap<>();
//        Map<String, Integer> laneTags = new HashMap<>();
//        network.getLinks().values().forEach(link -> {
//            int prevLaneCount = laneNumbers.getOrDefault(link.getNumberOfLanes(), 0);
//            laneNumbers.put(link.getNumberOfLanes(), prevLaneCount + 1);
//
//            String tags = String.valueOf(link.getAttributes().getAttribute(OsmTags.TURN_LANES_BACKWARD));
//            Arrays.stream(tags.split("[;|]")).forEach(tag -> {
//                int prevTagCount = laneTags.getOrDefault(tag, 0);
//                laneTags.put(tag, prevTagCount + 1);
//            });
//        });


//        try (BufferedWriter out = IOUtils.getBufferedWriter("./scenarios/krakow/krakow_intersections.csv");
//             CSVWriter csv = new CSVWriter(out)) {
//            csv.writeNext(new String[]{"signalSystemId", "nodeId", "x", "y"});
//            intersections.forEach((signalSystem, nodes) -> {
//                nodes.forEach(n -> {
//                    csv.writeNext(new String[]{
//                            signalSystem.getId().toString(),
//                            n.getId().toString(),
//                            String.valueOf(n.getCoord().getX()),
//                            String.valueOf(n.getCoord().getY())
//                    });
//                });
//            });
//        } catch (IOException ignored) {
//        }


//        Map<List<Object>, List<Link>> laneDirs = intersections.values().stream()
//                .flatMap(Collection::stream)
//                .flatMap(node -> node.getInLinks().values().stream())
//                .distinct()
//                .collect(Collectors.groupingBy(link -> {
//                    String lanes = (String) link.getAttributes().getAttribute(OsmTags.TURN_LANES);
//                    int outs = link.getToNode().getOutLinks().size();
//                    return lanes == null ? Arrays.asList(Collections.emptySet(), outs)
//                            : Arrays.asList(Stream.of(lanes.split("[|;]")).collect(Collectors.toSet()), outs);
//                }));

//        Map<Integer, List<Node>> allCounts = intersections.values().stream()
//                .flatMap(Collection::stream)
//                .collect(Collectors.groupingBy(node -> NetworkUtils.getIncidentLinks(node).size()));
//
//
//        Map<Integer, List<Node>> inCounts = intersections.values().stream()
//                .flatMap(Collection::stream)
//                .collect(Collectors.groupingBy(node -> node.getInLinks().size()));
//
//        Map<Integer, List<Node>> outCounts = intersections.values().stream()
//                .flatMap(Collection::stream)
//                .collect(Collectors.groupingBy(node -> node.getOutLinks().size()));
//
//        long diffCount = intersections.values().stream()
//                .flatMap(Collection::stream)
//                .filter(n -> n.getInLinks() != n.getOutLinks())
//                .count();
//
//         4. Fill toLinks of lanes
//        for (Set<ProcessedRelation> value : getSignalsParser().getNodeRestrictions().values()) {
//            for (ProcessedRelation processedRelation : value) {
//                 do something relations related
//                 find lanes stack in the yet to implement lanesStack property
//            }
//        }

        return null;
    }

    //todo
    // When signalized node is present on segment it must produce a signal for each lane,
    // so the lanes must be created first. The lanes should be created on the segment just before the signal,
    // as well as all the following segments up until the next intersection (link end) or next signal.
//    @Override
//    Collection<SupersonicOsmNetworkReader.WaySegment> createWaySegments(Map<Long, ProcessedOsmNode> nodes, ProcessedOsmWay way) {
//
//
//        Collection<SupersonicOsmNetworkReader.WaySegment> segments = super.createWaySegments(nodes, way);
//
//        for (int i = 0; i < way.getNodeIds().size(); i++) {
//
//            long node = way.getNodeIds().get(i);
//
//            if (getSignalsParser().getSignalizedNodes().containsKey(node)) {
//                // wenn nicht am ende, dann evtl. verschieben
//            }
//
//        }
//
//        return segments;
//    }


    private OsmSignalsParser getSignalsParser() {
        return (OsmSignalsParser) parser;
    }

    public Map<Long, String> getSignalizedNodes() {
        return getSignalsParser().getSignalizedNodes();
    }

    public static class Builder extends AbstractBuilder<OsmSignalsReader> {

        @Override
        OsmSignalsReader createInstance() {

            OsmSignalsParser parser = new OsmSignalsParser(coordinateTransformation,
                    linkProperties, includeLinkAtCoordWithHierarchy, Executors.newWorkStealingPool());

            return new OsmSignalsReader(parser, includeLinkAtCoordWithHierarchy, afterLinkCreated);
        }
    }
}
