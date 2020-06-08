package org.matsim.intensity;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public interface IntensityMonitor {

    double getIntensityForLink(final Id<Link> link, double time);
    double getAverageIntensityForLinkInInterval(final Id<Link> link, double startTime, double endTime);
    double getIntensityThreshold();
}
