package org.matsim.intensity;

import org.apache.log4j.Logger;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;

/**
 * Logs traffic stats for a given link - just to test traffic intensity metrics analyzers.
 */
public class TestDensityReporter implements StartupListener, LinkEnterEventHandler {

    private static final Logger LOGGER = Logger.getLogger(TestDensityReporter.class);
    private static final String message = "Statistics for a link %d at %f: density = %f, volume = %d, delay = %f ";

    private final DensityMonitor densityMonitor;
    private final VolumesAnalyzer volumesAnalyzer;
    private final DelayMonitor delayMonitor;
    private final Link monitoredLink;

    public TestDensityReporter(DensityMonitor densityMonitor, VolumesAnalyzer volumesAnalyzer, DelayMonitor delayMonitor, Link link) {
        this.densityMonitor = densityMonitor;
        this.volumesAnalyzer = volumesAnalyzer;
        this.delayMonitor = delayMonitor;
        this.monitoredLink = link;
    }

    @Override
    public void notifyStartup(StartupEvent startupEvent) {
        startupEvent.getServices().getEvents().addHandler(densityMonitor);
        startupEvent.getServices().getEvents().addHandler(volumesAnalyzer);
        startupEvent.getServices().getEvents().addHandler(this);
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        double time = linkEnterEvent.getTime() - 1.0; // for the density not to fluctuate
        double density = densityMonitor.getDensityForLink(monitoredLink.getId(), time);
        int[] volumes = volumesAnalyzer.getVolumesForLink(monitoredLink.getId());
        double delay = delayMonitor.getDelay(monitoredLink, time);

        LOGGER.info(String.format(message, monitoredLink.getId().index(), time, density, volumes[(int) Math.floor(time / 3600)], delay));
    }
}
