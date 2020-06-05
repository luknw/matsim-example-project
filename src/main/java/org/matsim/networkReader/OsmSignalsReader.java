package org.matsim.networkReader;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.SearchableNetwork;
import org.matsim.lanes.Lanes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;

public class OsmSignalsReader extends SupersonicOsmNetworkReader {

    OsmSignalsReader(OsmSignalsParser parser,
                     BiPredicate<Coord, Integer> includeLinkAtCoordWithHierarchy,
                     AfterLinkCreated afterLinkCreated) {
        super(parser,
                id -> parser.getSignalizedNodes().containsKey(id),
                includeLinkAtCoordWithHierarchy,
                (link, tags, direction) -> addSignalTags(link, tags, direction, afterLinkCreated));
    }

    private OsmSignalsParser getSignalsParser() {
        return (OsmSignalsParser) parser;
    }

    private static void addSignalTags(Link link, Map<String, String> tags, Direction direction, AfterLinkCreated outfacingCallback) {

        String turnLanes = tags.get("turn:lanes");
        // and the other tags
        //create lanes stack and safe in lanes stack field
        link.setNumberOfLanes(5000); // some reasonable number

        // it is important to call this callback, so that the outfacing api is as expected
        outfacingCallback.accept(link, tags, direction);
    }

    public Lanes getLanes() {
        throw new RuntimeException("not implemented");
    }

    public Map<Long, String> getSignalizedNodes() {
        return getSignalsParser().getSignalizedNodes();
    }

    @Override
    public Network read(Path inputFile) {
        Network network = super.read(inputFile);

        SearchableNetwork searchable = (SearchableNetwork) network;

        // 1. Find complex intersections

        for (Node node : network.getNodes().values()) {

            Coord coord = node.getCoord();
            Collection<Node> out = new ArrayList<>();
//            searchable.getNodeQuadTree().getRectangle(coord.getX() - 30, coord.getY() - 30, coord.getX() + 30, coord.getY() + 30, out);

            // create a new intersection node, which is going to be the single intersection
            // memorize all nodes which are part of that intersection, so they can be removed later

        }

        // 2. figure out vectors of turn restrictions with vectors

        // 3. merge intersection

        // 4. Fill toLinks of lanes
        for (Set<ProcessedRelation> value : getSignalsParser().getNodeRestrictions().values()) {
            for (ProcessedRelation processedRelation : value) {
                // do something relations related
                // find lanes stack in the yet to implement lanesStack property
            }
        }

        return network;
    }

    @Override
    Collection<SupersonicOsmNetworkReader.WaySegment> createWaySegments(Map<Long, ProcessedOsmNode> nodes, ProcessedOsmWay way) {


        Collection<SupersonicOsmNetworkReader.WaySegment> segments = super.createWaySegments(nodes, way);

        for (int i = 0; i < way.getNodeIds().size(); i++) {

            long node = way.getNodeIds().get(i);

            if (getSignalsParser().getSignalizedNodes().containsKey(node)) {
                // wenn nicht am ende, dann evtl. verschieben
            }

        }

        return segments;
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
