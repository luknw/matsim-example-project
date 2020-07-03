package pl.edu.agh.matsim.intensity;

import com.opencsv.CSVWriter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TrafficIntensityReporter implements IterationEndsListener {

    private final IntensityMonitor intensityMonitor;
    private final Network network;

    @Inject
    public TrafficIntensityReporter(IntensityMonitor intensityMonitor, Network network) {
        this.intensityMonitor = intensityMonitor;
        this.network = network;
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        save(event.getIteration(), getAverageIntensitiesForAllLinks());
    }

    private List<String[]> getAverageIntensitiesForAllLinks() {
        return network.getLinks().keySet().stream()
                .map(this::getAverageIntensitiesForLink)
                .collect(Collectors.toList());
    }

    private String[] getAverageIntensitiesForLink(Id<Link> linkId) {
        return Stream.concat(
                Stream.of(Integer.toString(linkId.index())),
                IntStream.range(0, 24 * 4)
                        .mapToDouble(startIdx -> intensityMonitor.getAverageIntensityForLinkInInterval(linkId, startIdx * 15 * 60, (startIdx + 1) * 15 * 60))
                        .mapToObj(Double::toString))
                .toArray(String[]::new);
    }

    private void save(int iteration, List<String[]> intensities) {
        try {
            String filename = "./output/traffic_intensity_" + iteration + ".csv";
            Writer writer = new FileWriter(new File(filename));
            CSVWriter csvWriter = new CSVWriter(writer);
            csvWriter.writeNext(getDescription());
            csvWriter.writeAll(intensities);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] getDescription() {
        return Stream.concat(
                Stream.of("linkId"),
                IntStream.range(0, 24 * 4)
                        .mapToObj(startIdx -> startIdx * 15 * 60 + "-" + (startIdx + 1) * 15 * 60))
                .toArray(String[]::new);
    }
}
