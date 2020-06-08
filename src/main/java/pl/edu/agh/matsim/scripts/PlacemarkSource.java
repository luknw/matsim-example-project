package pl.edu.agh.matsim.scripts;

import com.opencsv.CSVReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class PlacemarkSource {

    private static final CoordinateTransformation coordinateTransformation = TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:32634");
    private static final Random generator = new Random();
    private final List<Placemark> placemarks;

    public PlacemarkSource(String csvSourcePath) throws IOException {
        Reader reader = new FileReader(new File(csvSourcePath));
        CSVReader csvReader = new CSVReader(reader);
        placemarks = csvReader.readAll().stream()
                .map(strings -> new Placemark(strings[0], strings[1], strings[2]))
                .collect(Collectors.toList());
    }

    public Placemark getRandomPlacemark() {
        int randomIndex = generator.nextInt(placemarks.size());
        return placemarks.get(randomIndex);
    }

    public List<Placemark> getRandomPlacemarks(int n) {
        return generator.ints(n, 0, placemarks.size())
                .mapToObj(placemarks::get)
                .collect(Collectors.toList());
    }

    static class Placemark {

        private final Coord coordInEpsg;
        private final String name;

        Placemark(String name, String coordX, String coordY) {
            this.name = name;
            Coord coord = CoordUtils.createCoord(Double.parseDouble(coordX), Double.parseDouble(coordY));
            this.coordInEpsg = coordinateTransformation.transform(coord);
        }

        public String getName() {
            return name;
        }

        public Coord getCoordInEpsg() {
            return coordInEpsg;
        }
    }
}
