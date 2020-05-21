package org.matsim.density;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;

/**
 * Counts the number of vehicles in each link and returns current density
 */
public class InstantDensityMonitor implements LinkEnterEventHandler, LinkLeaveEventHandler {

    private final Network network;
    private final IdMap<Link, Integer> linkToNumberOfCars; //should we use AtomicInteger?

    InstantDensityMonitor(Network network, EventsManager eventsManager) {
        this.network = network;
        this.linkToNumberOfCars = new IdMap<>(Link.class);
        eventsManager.addHandler(this);
    }

    /**
     * @param linkId
     * @return Traffic density in a given link defined as the number of cars in a link divided by its capacity.
     */
    public double getDensityForLink(final Id<Link> linkId) {
        int numberOfCars = linkToNumberOfCars.getOrDefault(linkId, 0);
        double capacity = network.getLinks().get(linkId).getCapacity();
        return numberOfCars / capacity;
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        Id<Link> linkId = linkEnterEvent.getLinkId();
        linkToNumberOfCars.putIfAbsent(linkId, 0);
        linkToNumberOfCars.computeIfPresent(linkId, (id, old_value) -> old_value++);
    }

    @Override
    public void handleEvent(final LinkLeaveEvent linkLeaveEvent) {
        Id<Link> linkId = linkLeaveEvent.getLinkId();
        linkToNumberOfCars.computeIfPresent(linkId, (id, old_value) -> old_value--);
    }
}
