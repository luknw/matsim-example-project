package org.matsim.intensity;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;

import java.util.HashMap;
import java.util.TreeMap;

/**
 * Keeps the numbers of vehicles leaving each link in a point in time
 */
public class VolumesMonitor implements LinkLeaveEventHandler, IntensityMonitor {
    private final HashMap<Id<Link>, TreeMap<Double, Integer>> linkToVolumesAtTime;

    @Inject
    public VolumesMonitor(EventsManager manager) {
        linkToVolumesAtTime = new HashMap<>(Id.getNumberOfIds(Link.class)); // won't it cause memory issues?
        manager.addHandler(this);
    }

    @Override
    public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
        double time = linkLeaveEvent.getTime();
        Id<Link> linkId = linkLeaveEvent.getLinkId();
        TreeMap<Double, Integer> timeToVolume = getOrPutEmpty(linkId);
        int volumeAtTime = timeToVolume.getOrDefault(time, 0);
        timeToVolume.put(time, volumeAtTime + 1);
    }

    public int getVolume(Id<Link> linkId, double time) {
        TreeMap<Double, Integer> timeToVolume = getOrPutEmpty(linkId);
        return timeToVolume.getOrDefault(time, 0);
    }

    public int getVolume(Id<Link> linkId, double from, double to) {
        TreeMap<Double, Integer> timeToVolume = getOrPutEmpty(linkId);
        int sum = 0;
        return timeToVolume.subMap(from, to)
                .values().stream()
                .reduce(0, Integer::sum);
    }

    @Override
    public void reset(int iteration) {
        linkToVolumesAtTime.values()
                .forEach(TreeMap::clear);
    }

    private TreeMap<Double, Integer> getOrPutEmpty(Id<Link> linkId) {
        TreeMap<Double, Integer> emptyMap = new TreeMap<>();
        TreeMap<Double, Integer> result = linkToVolumesAtTime.putIfAbsent(linkId, emptyMap);
        return result != null ? result : emptyMap;
    }

    @Override
    public double getIntensityForLink(Id<Link> link, double time) {
        return getVolume(link, time);
    }

    @Override
    public double getAverageIntensityForLinkInInterval(Id<Link> link, double startTime, double endTime) {
        return getVolume(link, startTime, endTime);
    }

    @Override
    public double getIntensityThreshold() {
        return 5;
    }
}
