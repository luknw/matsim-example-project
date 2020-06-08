package pl.edu.agh.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerSignalController;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import pl.edu.agh.matsim.intensity.DelayMonitor;
import pl.edu.agh.matsim.intensity.DensityMonitor;
import pl.edu.agh.matsim.intensity.TestTrafficIntensityReporter;
import pl.edu.agh.matsim.intensity.VolumesMonitor;

import java.util.Collection;

public class RunMatsimForKrakow {
    private static final String path = "./scenarios/krakow/config.xml";

    private static Config config;
    private static Scenario scenario;
    private static Controler controler;

    private static void attachSignals() {
        SignalSystemsConfigGroup signalsConfigGroup = ConfigUtils.addOrGetModule(config,
                SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class);
        if (signalsConfigGroup.isUseSignalSystems()) {
            scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
            new Signals.Configurator(controler)
                    .addSignalControllerFactory(LaemmerSignalController.IDENTIFIER, LaemmerSignalController.LaemmerFactory.class);
        }
    }

    private static void attachMetrics() {
        DensityMonitor densityMonitor = new DensityMonitor(scenario, controler.getEvents());
        VolumesMonitor volumesMonitor = new VolumesMonitor(controler.getEvents());
        DelayMonitor delayMonitor = new DelayMonitor(scenario);

        TestTrafficIntensityReporter testTrafficIntensityReporter =
                new TestTrafficIntensityReporter(densityMonitor, volumesMonitor, delayMonitor,
                        (Collection<Link>) scenario.getNetwork().getLinks().values());
        controler.addControlerListener(testTrafficIntensityReporter);
    }

    public static void main(String[] args) {

        config = ConfigUtils.loadConfig(path);
        scenario = ScenarioUtils.loadScenario(config);
        controler = new Controler(scenario);

        attachSignals();
        attachMetrics();

        controler.run();
    }
}
