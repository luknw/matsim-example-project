package pl.edu.agh.matsim.intensity;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;

import java.util.Collection;

/**
 * Logs traffic stats for a given link - just to test traffic intensity metrics analyzers.
 * //TODO test average metrics calculated over some period
 */
public class TestTrafficIntensityReporter implements StartupListener, LinkEnterEventHandler {

    private static final Logger LOGGER = Logger.getLogger(TestTrafficIntensityReporter.class);
    private static final String message = "Statistics for a link %d at %f: density = %f, volume = %d, delay = %f ";

    private final DensityMonitor densityMonitor;
    private final VolumesMonitor volumesMonitor;
    private final DelayMonitor delayMonitor;
    private final Collection<Link> monitoredLinks;

    public TestTrafficIntensityReporter(DensityMonitor densityMonitor, VolumesMonitor volumesMonitor, DelayMonitor delayMonitor, Collection<Link> links) {
        this.densityMonitor = densityMonitor;
        this.volumesMonitor = volumesMonitor;
        this.delayMonitor = delayMonitor;
        this.monitoredLinks = links;
    }

    @Override
    public void notifyStartup(StartupEvent startupEvent) {
        startupEvent.getServices().getEvents().addHandler(densityMonitor);
        startupEvent.getServices().getEvents().addHandler(volumesMonitor);
        startupEvent.getServices().getEvents().addHandler(this);
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        double time = linkEnterEvent.getTime() - 1.0; // for the values not to fluctuate (processing events with getTime() timestamps is in progress)
        for (Link link : monitoredLinks) {
            double density = densityMonitor.getDensityForLink(link.getId(), time);
            int volume = volumesMonitor.getVolume(link.getId(), time);
            double delay = delayMonitor.getDelay(link, time);
            if (density > 0.0 || volume > 0.0) {
                LOGGER.info(String.format(message, link.getId().index(), time, density, volume, delay));
            }
        }
    }
}
