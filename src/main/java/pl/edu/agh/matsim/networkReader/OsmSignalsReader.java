package pl.edu.agh.matsim.networkReader;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataImpl;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.utils.SignalUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.intersectionSimplifier.DensityCluster;
import org.matsim.core.network.algorithms.intersectionSimplifier.containers.Cluster;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.nio.file.Path;
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

    private Network network;
    private Lanes lanes;
    private SignalsData signalsData;

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

    /**
     * Best effort matching that may produce over-connected or under-connected network in some cases.
     */
    private static Map<OsmTurnTagValue, Set<Link>> matchInDirectionsToOutLinks(
            Set<OsmTurnTagValue> inDirections,
            NavigableMap<Double, Link> outLinks
    ) {
        Map<OsmTurnTagValue, Set<Link>> inDirectionsToOutLinks = new HashMap<>();
        HashSet<Map.Entry<Double, Link>> unusedOutLinks = new HashSet<>(outLinks.entrySet());

        // 1. match directions to outlinks, to make sure each direction is paired
        inDirections.stream().collect(Collectors.toMap(Function.identity(), direction ->
                outLinks.entrySet().stream()
                        .min(Comparator.comparingDouble(angleLink ->
                                // plus inside abs, because outLink angles are negated
                                Math.abs(direction.getRadAngle() + angleLink.getKey())))
                        .get())
        ).forEach((inDirection, angleLink) -> {
            inDirectionsToOutLinks.computeIfAbsent(inDirection, ignore -> new HashSet<>()).add(angleLink.getValue());
            unusedOutLinks.remove(angleLink);
        });

        // 2. match outlinks to directions, to make sure each outlink is paired
        unusedOutLinks.stream().collect(Collectors.groupingBy(angleLink ->
                inDirections.stream().min(Comparator.comparingDouble(direction ->
                        // plus inside abs, because outLink angles are negated
                        Math.abs(direction.getRadAngle() + angleLink.getKey()))
                ).get()
        )).forEach((inDirection, outLinksEntries) -> {
            outLinksEntries.stream().map(Map.Entry::getValue).forEach(outLink ->
                    inDirectionsToOutLinks.computeIfAbsent(inDirection, ignore -> new HashSet<>()).add(outLink));
        });

        return inDirectionsToOutLinks;
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

    @Override
    public Network read(Path inputFile) {
        if (network != null) {
            return network;
        }
        network = super.read(inputFile);
        return network;
    }

    //todo collapse lanes with the same directions by incrementing represented lanes attribute
    public Lanes getLanes() {
        if (lanes != null) {
            return lanes;
        }

        lanes = LanesUtils.createLanesContainer();

        network.getLinks().values().forEach(link -> {

            LanesToLinkAssignment l2l = lanes.getFactory().createLanesToLinkAssignment(link.getId());
            lanes.addLanesToLinkAssignment(l2l);

            Id<Lane> olLaneId = Id.create(link.getId().toString() + ".0.ol", Lane.class);
            Lane olLane = lanes.getFactory().createLane(olLaneId);
            LanesUtils.calculateAndSetCapacity(olLane, false, link, network);
            olLane.setNumberOfRepresentedLanes(link.getNumberOfLanes());
            olLane.setStartsAtMeterFromLinkEnd(link.getLength());

            Map<Lane, Set<OsmTurnTagValue>> inLaneToDirections = new HashMap<>();

            Map<OsmTurnTagValue, Set<Link>> inDirectionToOutLink = createInLanes(link, l2l, inLaneToDirections);

            for (Lane lane : l2l.getLanes().values()) {
                olLane.addToLaneId(lane.getId());
                LanesUtils.calculateAndSetCapacity(lane, true, link, network);
                inLaneToDirections.get(lane).stream()
                        .map(inDirectionToOutLink::get)
                        .flatMap(Collection::stream)
                        .map(Identifiable::getId)
                        .distinct()
                        .forEach(lane::addToLinkId);
            }

            if (inLaneToDirections.isEmpty()) {
                link.getToNode().getOutLinks().values().forEach(outLink -> olLane.addToLinkId(outLink.getId()));
            }

            l2l.addLane(olLane);
        });

        return lanes;
    }

    private Map<OsmTurnTagValue, Set<Link>> createInLanes(Link link,
                                                          LanesToLinkAssignment l2l,
                                                          Map<Lane, Set<OsmTurnTagValue>> laneToDirections) {
        String[] laneStrs = getLinkLaneStrs(link);
        if (laneStrs.length == 0) {
            return Collections.emptyMap();
        }

        NavigableMap<Double, Link> outLinks = NetworkUtils.getOutLinksSortedClockwiseByAngle(link);
        if (outLinks.isEmpty()) {
            return Collections.emptyMap();
        }

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
            lane.setStartsAtMeterFromLinkEnd(Math.min(
                    link.getLength() / 1.1, // has to be lower than link length, so adjust slightly
                    45.0)); // matsim default
            lane.setNumberOfRepresentedLanes(1);
            lane.setAlignment(laneIndex);
            l2l.addLane(lane);

            laneToDirections.put(lane, laneDirections);
            return laneDirections;
        }).flatMap(Collection::stream).collect(Collectors.toSet());

        return matchInDirectionsToOutLinks(allInDirections, outLinks);
    }

    public SignalsData getSignalsData(String signalControllerIdentifier) {
        if (signalsData != null) {
            return signalsData;
        }

        signalsData = new SignalsDataImpl(new SignalSystemsConfigGroup());
        SignalSystemsData systems = signalsData.getSignalSystemsData();
        SignalGroupsData groups = signalsData.getSignalGroupsData();
        SignalControlData control = signalsData.getSignalControlData();

        // filter signal nodes from parser
        List<Node> signals = getSignalizedNodes().keySet().stream()
                .map(s -> (Node) network.getNodes().get(Id.createNodeId(s)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // cluster signals into systems
        double clusteringRadius = 150.0;

        DensityCluster signalSystemClusterer = new DensityCluster(signals, false);
        signalSystemClusterer.clusterInput(clusteringRadius, 1);
        List<Cluster> osmSignalSystems = signalSystemClusterer.getClusterList();

        osmSignalSystems.forEach(sys -> {
            SignalSystemData sysData = systems.getFactory().createSignalSystemData(Id.create(sys.getId(), SignalSystem.class));
            systems.addSignalSystemData(sysData);

            sys.getPoints().stream()
                    .flatMap(point -> point.getNode().getInLinks().values().stream())
                    .forEach(inLink -> {
                        LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(inLink.getId());
                        if (l2l == null) {
                            SignalData signalData = systems.getFactory().createSignalData(Id.create(inLink.getId(), Signal.class));
                            sysData.addSignalData(signalData);
                            signalData.setLinkId(inLink.getId());
                        } else {
                            l2l.getLanes().values().forEach(lane -> {
                                SignalData signalData = systems.getFactory().createSignalData(Id.create(lane.getId(), Signal.class));
                                sysData.addSignalData(signalData);
                                signalData.setLinkId(inLink.getId());
                                signalData.addLaneId(lane.getId());
                            });
                        }
                    });

            SignalUtils.createAndAddSignalGroups4Signals(groups, sysData);

            SignalSystemControllerData controller = control.getFactory().createSignalSystemControllerData(sysData.getId());
            control.addSignalSystemControllerData(controller);

            controller.setControllerIdentifier(signalControllerIdentifier);
        });

        return signalsData;
    }

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

    //todo
    // When signalized node is present on segment it must produce a signal for each lane,
    // so the lanes must be created first. The lanes should be created on the segment just before the signal,
    // as well as all the following segments up until the next intersection (link end) or next signal.
//    @Override
//    Collection<SupersonicOsmNetworkReader.WaySegment> createWaySegments(Map<Long, ProcessedOsmNode> nodes, ProcessedOsmWay way) {
//
//        Collection<SupersonicOsmNetworkReader.WaySegment> segments = super.createWaySegments(nodes, way);
//
//        IntStream.range(0, way.getNodeIds().size())
//                .mapToLong(i -> way.getNodeIds().get(i))
//                .filter(nodeId -> getSignalizedNodes().containsKey(nodeId))
//                .mapToObj(nodeId -> );
//
//        for (int i = 0; i < way.getNodeIds().size(); i++) {
//
//            long node = way.getNodeIds().get(i);
//
//            if (getSignalsParser().getSignalizedNodes().containsKey(node)) {
//                 wenn nicht am ende, dann evtl. verschieben
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
