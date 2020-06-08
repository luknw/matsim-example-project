package pl.edu.agh.matsim.intensity;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

/**
 * Calculates links' delays understood as the difference between real travel time, and the one set in the configuration
 * //TODO I have no idea if it's gonna work because I don't know where/when is freespeed updated and if real travel time = length / freespeed(time) even with traffic signals...?
 */
public class DelayMonitor implements IntensityMonitor{

    private final Map<Id<Link>, ? extends Link> links;

    @Inject
    public DelayMonitor(Scenario scenario) {
        links = scenario.getNetwork().getLinks();
    }

    public double getDelay(Link link, double time) {
        double realTravelTime = link.getLength() / link.getFreespeed(time);
        double configuredTravelTime = link.getLength() / link.getFreespeed();
        return realTravelTime - configuredTravelTime;
    }

    /**
     * I'm not sure if it's even a good idea to do it like that...
     * - calculating average by sampling with interval between samples equal to the given parameter
     */
    public double getAverageDelay(Link link, double startTime, double endTime, double samplingInterval) {
        double configuredTravelTime = link.getLength() / link.getFreespeed();
        double averageFreespeed = getAverageFreespeed(startTime, endTime, samplingInterval).orElse(0.0);
        double averageTravelTime = link.getLength() / averageFreespeed;
        return averageTravelTime - configuredTravelTime;
    }

    private OptionalDouble getAverageFreespeed(double startTime, double endTime, double samplingInterval) {
        int numberOfSamples = (int) Math.round((endTime - startTime) / samplingInterval + 1.0);
        return IntStream.range(1, numberOfSamples + 1)
                .asDoubleStream()
                .map(value -> value * samplingInterval)
                .average();
    }

    @Override
    public double getIntensityForLink(Id<Link> link, double time) {
        return getDelay(links.get(link), time);
    }

    @Override
    public double getAverageIntensityForLinkInInterval(Id<Link> link, double startTime, double endTime) {
        return getAverageDelay(links.get(link), startTime, endTime, 1.0);
    }

    @Override
    public double getIntensityThreshold() {
        return 0.0;
    }
}
