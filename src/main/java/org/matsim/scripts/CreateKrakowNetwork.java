package org.matsim.scripts;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.OsmNetworkReader;

/**
 * krakow_roads.osm file obtained by dowloading file from here http://download.geofabrik.de/europe/poland/malopolskie.html
 * and truncaiting it to 50.1668 - 49.9265N ; 19.6854 - 20.3240E using osmosis tool:
 * osmosis --rb file="malopolskie-latest.osm" --bounding-box left=19.6854 right=20.3240 top=50.1668 bottom=49.9265 completeWays=true --used-node --wx file="krakow_roads.osm"
 */
public class CreateKrakowNetwork {

    public static void main(String[] args) {

        Network network = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getNetwork();
        CoordinateTransformation coordinateTransformation = TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:32634");
        OsmNetworkReader osmNetworkReader = new OsmNetworkReader(network, coordinateTransformation);
        osmNetworkReader.setKeepPaths(false);
        osmNetworkReader.parse("./scenarios/krakow/krakow_roads.osm");
        new NetworkCleaner().run(network); // do we want it?
        NetworkWriter networkWriter = new NetworkWriter(network);
        networkWriter.write("./scenarios/krakow/network.xml");

    }
}
