package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.intensity.DelayMonitor;
import org.matsim.intensity.DensityMonitor;
import org.matsim.intensity.TestTrafficIntensityReporter;
import org.matsim.intensity.VolumesMonitor;

import java.util.Collection;

public class RunMatsimForKrakow {
    private static final String path = "./scenarios/krakow/config.xml";

    public static void main(String[] args) {

        Config config = ConfigUtils.loadConfig(path);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Controler controler = new Controler(scenario);

        DensityMonitor densityMonitor = new DensityMonitor(scenario.getNetwork());
        VolumesMonitor volumesMonitor = new VolumesMonitor();
        DelayMonitor delayMonitor = new DelayMonitor();

        //TODO test with 50,000+ vehicles (with 500 is fine)
       TestTrafficIntensityReporter testTrafficIntensityReporter =
               new TestTrafficIntensityReporter(densityMonitor, volumesMonitor, delayMonitor,
                       (Collection<Link>) scenario.getNetwork().getLinks().values());
       controler.addControlerListener(testTrafficIntensityReporter);

        controler.run();
    }
}
