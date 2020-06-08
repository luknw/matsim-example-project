package pl.edu.agh.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import pl.edu.agh.matsim.intensity.DelayMonitor;
import pl.edu.agh.matsim.intensity.DensityMonitor;
import pl.edu.agh.matsim.intensity.TestTrafficIntensityReporter;
import pl.edu.agh.matsim.intensity.VolumesMonitor;

import java.net.URL;
import java.util.Collection;

public class RunMatsimFromExamplesUtils {

    public static void main(String[] args) {

        // need to add matsim-examples in pom.xml for this class to work

        URL context = org.matsim.examples.ExamplesUtils.getTestScenarioURL("equil");
        URL url = IOUtils.extendUrl(context, "config.xml");

        Config config = ConfigUtils.loadConfig(url);

        // ---

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // ---

        Controler controler = new Controler(scenario);
        DensityMonitor densityMonitor = new DensityMonitor(scenario, controler.getEvents());
        VolumesMonitor volumesMonitor = new VolumesMonitor(controler.getEvents());
        DelayMonitor delayMonitor = new DelayMonitor(scenario);

        TestTrafficIntensityReporter testTrafficIntensityReporter =
                new TestTrafficIntensityReporter(densityMonitor, volumesMonitor, delayMonitor,
                        (Collection<Link>) scenario.getNetwork().getLinks().values());
        controler.addControlerListener(testTrafficIntensityReporter);


        // ---

        controler.run();

    }

}
