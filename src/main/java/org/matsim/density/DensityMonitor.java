package org.matsim.density;

import com.google.common.collect.Iterables;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.vehicles.Vehicle;

import java.util.List;

/**
 * Keeps time intervals vehicles spend in each link and calculates links' density understood as the number of cars
 * in the link divided by its capacity.
 * //TODO investigate impact on MATSIM's performance
 */
public class DensityMonitor implements LinkEnterEventHandler, LinkLeaveEventHandler {

    private final Network network;
    private final IdMap<Link, IdMap<Vehicle, List<TimeInterval>>> linkToVehiclesStatistics; //should we care about thread safety?

    DensityMonitor(Network network, EventsManager eventsManager) {
        this.network = network;
        this.linkToVehiclesStatistics = new IdMap<>(Link.class);
        eventsManager.addHandler(this);
    }

    /**
     * @return vehicles' density in a link at the given point in time
     */
    public double getDensityForLink(final Id<Link> linkId, double time) {
        IdMap<Vehicle, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
        double capacity = network.getLinks().get(linkId).getCapacity();
        int numberOfCars = vehicleToTimeInterval.values().stream()
                .flatMap(List::stream)
                .map(timeInterval -> timeInterval.include(time))
                .mapToInt(bool -> bool ? 1 : 0)
                .sum();
        return numberOfCars / capacity;
    }

    /**
     * @return vehicles' density in a link in the given period
     */
    public double getAverageDensityForLinkInInterval(final Id<Link> linkId, double startTime, double endTime) {
        IdMap<Vehicle, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
        double capacity = network.getLinks().get(linkId).getCapacity();
        double commonIntervalsSum = vehicleToTimeInterval.values().stream()
                .flatMap(List::stream)
                .map(timeInterval -> timeInterval.getCommonDuration(startTime, endTime))
                .mapToDouble(Double::doubleValue)
                .sum();
        return commonIntervalsSum / (endTime - startTime) / capacity;
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        Id<Vehicle> vehicleId = linkEnterEvent.getVehicleId();
        Id<Link> linkId = linkEnterEvent.getLinkId();
        double time = linkEnterEvent.getTime();
        IdMap<Vehicle, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
        vehicleToTimeInterval.get(vehicleId).add(new TimeInterval(time));
    }

    @Override
    public void handleEvent(final LinkLeaveEvent linkLeaveEvent) {
        Id<Vehicle> vehicleId = linkLeaveEvent.getVehicleId();
        Id<Link> linkId = linkLeaveEvent.getLinkId();
        double time = linkLeaveEvent.getTime();
        IdMap<Vehicle, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
        Iterables.getLast(vehicleToTimeInterval.get(vehicleId)).setLeaveTime(time);
    }

    @Override
    public void reset(int iteration) {
        linkToVehiclesStatistics.values().stream()
                .flatMap(map -> map.values().stream())
                .forEach(List::clear);
    }

    private IdMap<Vehicle, List<TimeInterval>> getOrPutEmpty(Id<Link> linkId) {
        IdMap<Vehicle, List<TimeInterval>> emptyMap = new IdMap<>(Vehicle.class);
        IdMap<Vehicle, List<TimeInterval>> result = linkToVehiclesStatistics.putIfAbsent(linkId, emptyMap);
        return result != null ? result : emptyMap;
    }

    private static class TimeInterval {
        private double enterTime;
        private double leaveTime;

        TimeInterval(double enterTime) {
            this.enterTime = enterTime;
            this.leaveTime = Double.POSITIVE_INFINITY;
        }

        public void setLeaveTime(double leaveTime) {
            this.leaveTime = leaveTime;
        }

        boolean include(double time) {
            return enterTime <= time && leaveTime > time;
        }

        double getCommonDuration(double enterTime, double leaveTime) {
            double common = Math.min(this.leaveTime, leaveTime) - Math.max(this.enterTime, enterTime);
            return common > 0 ? common : 0;
        }
    }
}
