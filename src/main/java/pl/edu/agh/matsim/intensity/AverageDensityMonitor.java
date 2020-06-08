package pl.edu.agh.matsim.intensity;

import com.google.common.collect.Lists;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;

import java.util.ArrayList;

/**
 * Counts the number of vehicles in each link and returns average density (probed once a second) in the monitor's time period
 * WARNING: most likely this class can by replaced with DensityMonitor, but their impact on performance should be evaluated first
 */
@Deprecated()
public class AverageDensityMonitor implements LinkEnterEventHandler, LinkLeaveEventHandler {
    private final Network network;
    private final int timePeriod;
    private final IdMap<Link, LinkDensityCounter> linkToDensityCounter;

    AverageDensityMonitor(Network network, int timePeriod, EventsManager eventsManager) {
        this.network = network;
        this.timePeriod = timePeriod;
        this.linkToDensityCounter = new IdMap<>(Link.class);
        eventsManager.addHandler(this);
    }

    public double getDensityForLink(final Id<Link> linkId, int timestamp) {
        LinkDensityCounter counter = linkToDensityCounter.getOrDefault(linkId, new LinkDensityCounter(timePeriod));
        double capacity = network.getLinks().get(linkId).getCapacity();
        return counter.getAverageNumberOfCars(timestamp) / capacity;
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        Id<Link> linkId = linkEnterEvent.getLinkId();
        LinkDensityCounter counter = linkToDensityCounter.putIfAbsent(linkId, new LinkDensityCounter(timePeriod));
        counter.increment((int) linkEnterEvent.getTime()); //TODO figure out if it's in seconds
    }

    @Override
    public void handleEvent(final LinkLeaveEvent linkLeaveEvent) {
        Id<Link> linkId = linkLeaveEvent.getLinkId();
        LinkDensityCounter counter = linkToDensityCounter.putIfAbsent(linkId, new LinkDensityCounter(timePeriod));
        counter.decrement((int) linkLeaveEvent.getTime());
    }

    @Override
    public void reset(int iteration) {
        linkToDensityCounter.keySet().stream().forEach(linkToDensityCounter::remove);
    }

    class LinkDensityCounter {

        private final ArrayList<DensityWithTimestamp> numberOfCarsWithTimestamp;
        private final int timePeriod;
        private int numberOfCars;

        LinkDensityCounter(int timePeriod) {
            this.timePeriod = timePeriod;
            numberOfCars = 0;
            numberOfCarsWithTimestamp = Lists.newArrayListWithCapacity(timePeriod);
            for (int index = 0; index < timePeriod; index++) {
                numberOfCarsWithTimestamp.set(index, new DensityWithTimestamp());
            }
        }

        LinkDensityCounter increment(int timestamp) {
            numberOfCars++;
            numberOfCarsWithTimestamp.get(timestamp % timePeriod).set(numberOfCars, timestamp);
            return this;
        }

        LinkDensityCounter decrement(int timestamp) {
            numberOfCars--;
            numberOfCarsWithTimestamp.get(timestamp % timePeriod).set(numberOfCars, timestamp);
            return this;
        }

        double getAverageNumberOfCars(int timestamp) {
            int sumOfCarSums = 0;
            int lastCarSum = numberOfCars;
            for (int i = 0; i < timePeriod; i++) {
                int index = (timestamp + timePeriod - i) % timePeriod;
                if ((timestamp - numberOfCarsWithTimestamp.get(index).getTimestamp()) < timePeriod) {
                    lastCarSum = numberOfCarsWithTimestamp.get(index).getNumberOfCars();
                }
                sumOfCarSums += lastCarSum;
            }
            return sumOfCarSums / (double) timePeriod;
        }

        class DensityWithTimestamp {
            private int timestamp;
            private int numberOfCars;

            DensityWithTimestamp() {
                timestamp = 0;
                numberOfCars = 0;
            }

            public void set(int numberOfCars, int timestamp) {
                this.numberOfCars = numberOfCars;
                this.timestamp = timestamp;
            }

            public int getNumberOfCars() {
                return numberOfCars;
            }

            public int getTimestamp() {
                return timestamp;
            }
        }
    }
}
