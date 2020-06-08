package pl.edu.agh.matsim.intensity;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.List;

/**
 * Keeps time intervals vehicles spend in each link and calculates links' density understood as the number of cars
 * in the link divided by its capacity.
 * //TODO investigate impact on MATSIM's performance - maybe calculating point/interval intersection with vehicleToTimeInterval should be done smarter - IntervalTree?
 */
public class DensityMonitor implements LinkEnterEventHandler, LinkLeaveEventHandler, VehicleEntersTrafficEventHandler, IntensityMonitor {

    private final Network network;
    //should we care about thread safety?
    private final HashMap<Id<Link>, HashMap<Id<Vehicle>, List<TimeInterval>>> linkToVehiclesStatistics; // I think there is no need to use TreeSet

    @Inject
    public DensityMonitor(Scenario scenario, EventsManager manager) {
        this.network = scenario.getNetwork();
        this.linkToVehiclesStatistics = new HashMap<>(); //what about predefined size??
        manager.addHandler(this);
    }

    /**
     * @return vehicles' density in a link at the given point in time
     */
    public double getDensityForLink(final Id<Link> linkId, double time) {
        HashMap<Id<Vehicle>, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
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
        HashMap<Id<Vehicle>, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
        double capacity = network.getLinks().get(linkId).getCapacity();
        double commonIntervalsSum = vehicleToTimeInterval.values().stream()
                .flatMap(List::stream)
                .map(timeInterval -> timeInterval.getCommonDuration(startTime, endTime))
                .mapToDouble(Double::doubleValue)
                .sum();
        return commonIntervalsSum / (endTime - startTime) / capacity;
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent vehicleEntersTrafficEvent) {
        Id<Link> linkId = vehicleEntersTrafficEvent.getLinkId();
        Id<Vehicle> vehicleId = vehicleEntersTrafficEvent.getVehicleId();
        double time = vehicleEntersTrafficEvent.getTime();
        addVehicleToLink(vehicleId, linkId, time);
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        Id<Vehicle> vehicleId = linkEnterEvent.getVehicleId();
        Id<Link> linkId = linkEnterEvent.getLinkId();
        double time = linkEnterEvent.getTime();
        addVehicleToLink(vehicleId, linkId, time);
    }

    @Override
    public void handleEvent(final LinkLeaveEvent linkLeaveEvent) {
        Id<Vehicle> vehicleId = linkLeaveEvent.getVehicleId();
        Id<Link> linkId = linkLeaveEvent.getLinkId();
        double time = linkLeaveEvent.getTime();
        removeVehicleFromLink(vehicleId, linkId, time);
    }

    @Override
    public void reset(int iteration) {
        linkToVehiclesStatistics.values().stream()
                .flatMap(map -> map.values().stream())
                .forEach(List::clear);
    }

    private void addVehicleToLink(Id<Vehicle> vehicleId, Id<Link> linkId, double time) {
        HashMap<Id<Vehicle>, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
        vehicleToTimeInterval.putIfAbsent(vehicleId, Lists.newArrayList());
        vehicleToTimeInterval.get(vehicleId).add(new TimeInterval(time));
    }

    private void removeVehicleFromLink(Id<Vehicle> vehicleId, Id<Link> linkId, double time) {
        HashMap<Id<Vehicle>, List<TimeInterval>> vehicleToTimeInterval = getOrPutEmpty(linkId);
        Iterables.getLast(vehicleToTimeInterval.get(vehicleId)).setLeaveTime(time);
    }

    private HashMap<Id<Vehicle>, List<TimeInterval>> getOrPutEmpty(Id<Link> linkId) {
        HashMap<Id<Vehicle>, List<TimeInterval>> emptyMap = new HashMap<>();
        HashMap<Id<Vehicle>, List<TimeInterval>> result = linkToVehiclesStatistics.putIfAbsent(linkId, emptyMap);
        return result != null ? result : emptyMap;
    }

    @Override
    public double getIntensityForLink(Id<Link> link, double time) {
        return getDensityForLink(link, time);
    }

    @Override
    public double getAverageIntensityForLinkInInterval(Id<Link> link, double startTime, double endTime) {
        return getAverageDensityForLinkInInterval(link, startTime, endTime);
    }

    @Override
    public double getIntensityThreshold() {
        return 0.1;
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
