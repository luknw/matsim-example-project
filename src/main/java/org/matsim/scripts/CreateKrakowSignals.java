package org.matsim.scripts;

import com.opencsv.CSVWriter;
import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.xml.dynsax.OsmXmlReader;
import org.matsim.api.core.v01.BasicLocation;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.SearchableNetwork;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CreateKrakowSignals {

    public static void main(String[] args) throws IOException, OsmInputException {
        SearchableNetwork network = (SearchableNetwork) NetworkUtils.readNetwork("./scenarios/krakow/network.xml");
        CoordinateTransformation transform = TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:32634");

        Set<Node> crossings = new HashSet<>();

        OsmXmlReader signalReader = new OsmXmlReader("./scenarios/krakow/krakow_traffic_signals.osm", true);
        signalReader.setHandler(new DefaultOsmHandler() {
            @Override
            public void handle(OsmNode osmNode) {
                Coord signalCoord = transform.transform(new Coord(osmNode.getLongitude(), osmNode.getLatitude()));
                Node nearestNode = network.getNearestNode(signalCoord);
                crossings.add(nearestNode);
            }
        });
        signalReader.read();

        try (BufferedWriter out = IOUtils.getBufferedWriter("./scenarios/krakow/krakow_traffic_signals.csv");
             CSVWriter csv = new CSVWriter(out)) {
            csv.writeNext(new String[]{"x", "y"});
            crossings.stream()
                    .flatMap(c -> c.getInLinks().values().stream())
                    .map(BasicLocation::getCoord)
                    .map(c -> new String[]{String.valueOf(c.getX()), String.valueOf(c.getY())})
                    .forEach(csv::writeNext);
        }
    }
}
