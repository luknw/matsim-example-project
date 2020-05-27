package org.matsim.project;

import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.intensity.DelayMonitor;
import org.matsim.intensity.DensityMonitor;
import org.matsim.intensity.TestDensityReporter;

import java.net.URL;

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
        TestDensityReporter testDensityReporter =
                new TestDensityReporter(new DensityMonitor(scenario.getNetwork()),
                        new VolumesAnalyzer(3600, 86399, scenario.getNetwork()),
                        new DelayMonitor(),
                        scenario.getNetwork().getLinks().values().iterator().next());
        controler.addControlerListener(testDensityReporter);

        // ---

        controler.run();

    }

}
