package org.matsim.scripts;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
 * krakow_roads.osm file obtained by downloading file from http://download.geofabrik.de/europe/poland/malopolskie.html
 * and truncating it to 49.9265 - 50.1668N ; 19.6854 - 20.3240E
 */
public class CreateKrakowNetwork {

    /**
     * Transforms normal longitude / latitude coordinates to the ones used by Matsim.
     */
    private static final CoordinateTransformation TRANSFORM =
            TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:32634");

    private static final Coord MIN_BOUND = TRANSFORM.transform(new Coord(19.6854, 49.9265));
    private static final Coord MAX_BOUND = TRANSFORM.transform(new Coord(20.3240, 50.1668));

    public static void main(String[] args) {

        SupersonicOsmNetworkReader osmNetworkReader = SupersonicOsmNetworkReader.builder()
                .coordinateTransformation(TRANSFORM)
                .includeLinkAtCoordWithHierarchy((coord, hierarchy) ->
                        MIN_BOUND.getX() <= coord.getX() && coord.getX() <= MAX_BOUND.getX()
                                && MIN_BOUND.getY() <= coord.getY() && coord.getY() <= MAX_BOUND.getY())
                .build();

        Network network = osmNetworkReader.read("./scenarios/krakow/malopolskie-latest.osm.pbf");

        new NetworkCleaner().run(network); // do we want it?
        NetworkWriter networkWriter = new NetworkWriter(network);
        networkWriter.write("./scenarios/krakow/network.xml");

    }
}
