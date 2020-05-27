package org.matsim.intensity;

import org.matsim.api.core.v01.network.Link;

import java.util.OptionalDouble;
import java.util.stream.IntStream;

/**
 * Calculates links' delays understood as the difference between real travel time, and the one set in the configuration
 * //TODO I have no idea if it's gonna work because I don't know where/when is freespeed updated and if real travel time = length / freespeed(time) even with traffic signals...?
 */
public class DelayMonitor {

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
}
