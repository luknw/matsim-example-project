package pl.edu.agh.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerSignalController;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.contrib.signals.data.SignalsScenarioWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import pl.edu.agh.matsim.signal.IntensityAdaptiveSignalController;
import pl.edu.agh.matsim.signal.SignalsModule;

/**
 * Runs Krakow scenario with adaptive signals control.
 * Xml files needs to be generated with CreateKrakowSignals script calling addAdaptiveSignalSystemController
 * in populateSignalsData method. In the future CreateKrakowSignals should be abstract.
 */
public class RunMatsimWithAdaptiveSignals {
    private static final String path = "./scenarios/krakow/config.xml";
    private static final String systemPath = "./signal_systems.xml";
    private static final String groupPath = "./signal_groups.xml";
    private static final String controlPath = "./signal_control.xml";


    private static Config config;
    private static Scenario scenario;
    private static Controler controler;

    private static void attachSignals() {
        SignalSystemsConfigGroup signalsConfigGroup = ConfigUtils.addOrGetModule(config,
                SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class);
        if (signalsConfigGroup.isUseSignalSystems()) {
            signalsConfigGroup.setSignalSystemFile(systemPath);
            signalsConfigGroup.setSignalGroupsFile(groupPath);
            signalsConfigGroup.setSignalControlFile(controlPath);
            scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
            controler.addOverridingModule(new SignalsModule()); /* so we can inject our classes bound in */
          //  new Signals.Configurator(controler)
          //          .addSignalControllerFactory(IntensityAdaptiveSignalController.IDENTIFIER, IntensityAdaptiveSignalController.AdaptiveSignalControllerFactory.class);
            new Signals.Configurator(controler)
                    .addSignalControllerFactory(LaemmerSignalController.IDENTIFIER, LaemmerSignalController.LaemmerFactory.class);
        }
    }

    private static void saveSignalControls() {
        /* save adapted signals */
        SignalsScenarioWriter signalsWriter = new SignalsScenarioWriter();
        signalsWriter.setSignalControlOutputFilename("./scenarios/krakow/signal_control.xml");
        SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
        signalsWriter.writeSignalControlData(signalsData.getSignalControlData());
    }

    public static void main(String[] args) {

        config = ConfigUtils.loadConfig(path);
        scenario = ScenarioUtils.loadScenario(config);
        controler = new Controler(scenario);

        attachSignals();

        controler.run();
        saveSignalControls();
    }
}
