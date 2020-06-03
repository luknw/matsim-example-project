package org.matsim.signal;

import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.signals.controller.AbstractSignalController;
import org.matsim.contrib.signals.controller.SignalController;
import org.matsim.contrib.signals.controller.SignalControllerFactory;
import org.matsim.contrib.signals.controller.fixedTime.DefaultPlanbasedSignalSystemController;
import org.matsim.contrib.signals.controller.fixedTime.DefaultPlanbasedSignalSystemController.FixedTimeFactory;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.model.*;
import org.matsim.core.controler.ControlerListenerManager;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.intensity.DelayMonitor;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Based on SimpleResponsiveSignal from org.matsim.codeexamples.simpleResponsiveSignalEngine.
 * Assumes that every junction has 24h / ADAPTATION_INTERVAL plans for every ADAPTATION_INTERVAL so it can analyze traffic and adapt discretely
 * upcoming plans
 * //TODO save new plans after simulation
 * //TODO change DelayMonitor or use DensityMonitor instead - could be even better?
 */
public class DelayAdaptiveSignalController extends AbstractSignalController implements AfterMobsimListener {

    private static final Logger LOG = Logger.getLogger(DelayAdaptiveSignalController.class);
    public static final String IDENTIFIER = "DelayAdaptiveSignalControl";

    private static final int ADAPTATION_INTERVAL = 15 * 60; /* adapt plans every ADAPTATION_INTERVAL */

    private final DelayMonitor delayMonitor;
    private final Scenario scenario;
    private int lastUpdateMarker; /* calculated as time of the last plan update divided by ADAPTATION_INTERVAL (floor) */

    private SignalController planBasedSignalController;
    private LinkedList<SignalPlan> planQueue; /* planBasedSignalController's planQueue */

    private DelayAdaptiveSignalController(Scenario scenario, DelayMonitor delayMonitor) {
        super();
        this.scenario = scenario;
        this.delayMonitor = delayMonitor;
        this.lastUpdateMarker = 0;
    }

    @Override
    public void notifyAfterMobsim(AfterMobsimEvent event) {
        // save adapted signals!
    }

    /**
     * updates current and next plan according to the delays on links if necessary
     */
    private void updateSignalsIfNecessary(double updateTime) {

        Map<SignalGroup, Double> signalGroupToDelayOnLinks = getSignalGroupToDelayOnLinks(updateTime);
        double sumOfDelays = signalGroupToDelayOnLinks.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sumOfDelays > 0.0) { /* improve plans only if delays exist */
            signalGroupToDelayOnLinks.replaceAll((group, d) -> d / sumOfDelays);
            Map<Id<SignalPlan>, SignalPlanData> signalPlanData = getSignalPlanData();
            SignalPlanData current = signalPlanData.get(planQueue.getLast().getId());
            updatePlan(current, signalGroupToDelayOnLinks);
            SignalPlanData next = signalPlanData.get(planQueue.getFirst().getId());
            updatePlan(next, signalGroupToDelayOnLinks);
        } else {
            /* do nothing */
            LOG.info("Signal control unchanged.");
        }
    }

    /**
     * updates plan according to the delays on links
     * //TODO update plan somehow according to the delays
     */
    private void updatePlan(SignalPlanData planData, Map<SignalGroup, Double> signalGroupToDelayOnLinks) {
       /* SortedMap<Id<SignalGroup>, SignalGroupSettingsData> signalGroupSettings = signalPlan.getSignalGroupSettingsDataByGroupId();
        SignalGroupSettingsData group1Setting = signalGroupSettings.get(Id.create("SignalGroup1", SignalGroup.class));
        SignalGroupSettingsData group2Setting = signalGroupSettings.get(Id.create("SignalGroup2", SignalGroup.class));

        // shift green time by one second depending on which delay is higher
        double delaySignalGroup1 = link2avgDelay.get(Id.createLinkId("2_3")) + link2avgDelay.get(Id.createLinkId("4_3"));
        double delaySignalGroup2 = link2avgDelay.get(Id.createLinkId("7_3")) + link2avgDelay.get(Id.createLinkId("8_3"));
        int greenTimeShift = (int) Math.signum(delaySignalGroup1 - delaySignalGroup2);

        // group1 onset = 0, group2 dropping = 55. signal switch should stay inside this interval
        if (greenTimeShift != 0 && group1Setting.getDropping() + greenTimeShift > 0 && group2Setting.getOnset() + greenTimeShift < 55){
            group1Setting.setDropping(group1Setting.getDropping() + greenTimeShift);
            group2Setting.setOnset(group2Setting.getOnset() + greenTimeShift);
            LOG.info("SignalGroup1: onset " + group1Setting.getOnset() + ", dropping " + group1Setting.getDropping());
            LOG.info("SignalGroup2: onset " + group2Setting.getOnset() + ", dropping " + group2Setting.getDropping());
            /* the new plan needs to be added to the signal control since it was made persistent over the iterations
             * and is not built newly each iteration from the data. theresa, jan'20 */
        addPlan(new DatabasedSignalPlan(planData));
    }

    private Map<SignalGroup, Double> getSignalGroupToDelayOnLinks(double updateTime) {
        return system.getSignalGroups().values().stream()
                .collect(Collectors.toMap(Function.identity(),
                        signalGroup -> signalGroup.getSignals().values().stream()
                                .map(signal -> getDelayOnSignalsLink(signal, updateTime))
                                .mapToDouble(Double::doubleValue).sum()));
    }

    private double getDelayOnSignalsLink(Signal signal, double updateTime) {
        Link link = scenario.getNetwork().getLinks().get(signal.getLinkId());
        return delayMonitor.getAverageDelay(link, updateTime - ADAPTATION_INTERVAL, updateTime, 1.0);
    }

    private Map<Id<SignalPlan>, SignalPlanData> getSignalPlanData() {
        SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
        SignalControlData signalControl = signalsData.getSignalControlData();
        SignalSystemControllerData signalControlSystem = signalControl.getSignalSystemControllerDataBySystemId().get(system.getId());
        return signalControlSystem.getSignalPlanData();
    }

    @Override
    public void setSignalSystem(SignalSystem signalSystem) {
        super.setSignalSystem(signalSystem);
        planBasedSignalController = new FixedTimeFactory().createSignalSystemController(signalSystem);
    }

    @Override
    public void addPlan(SignalPlan plan) {
        super.addPlan(plan);
        planBasedSignalController.addPlan(plan);
    }

    @Override
    public void updateState(double timeSeconds) {
        /* adapt plans every ADAPTATION_INTERVAL seconds */
        if (planShouldBeAdapted(timeSeconds)) {
            lastUpdateMarker++;
            updateSignalsIfNecessary(timeSeconds);
        }
        planBasedSignalController.updateState(timeSeconds);
    }

    private boolean planShouldBeAdapted(double timeSeconds) {
        return Math.floor(timeSeconds / ADAPTATION_INTERVAL) > lastUpdateMarker;
    }

    @Override
    public void simulationInitialized(double simStartTimeSeconds) {
        planBasedSignalController.simulationInitialized(simStartTimeSeconds);
        /* very ugly and hacky but an alternative is to copy the sourcecode of DefaultPlanbasedSignalSystemController  */
        try {
            Field planQueueField = null;
            planQueueField = DefaultPlanbasedSignalSystemController.class.getDeclaredField("planQueue");
            planQueueField.setAccessible(true);
            LinkedList<SignalPlan> planQueue = (LinkedList<SignalPlan>) planQueueField.get(planBasedSignalController);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public final static class DelayAdaptiveSignalControllerFactory implements SignalControllerFactory {

        @Inject
        ControlerListenerManager manager;
        @Inject
        Scenario scenario;
        @Inject
        DelayMonitor delayMonitor;

        @Override
        public SignalController createSignalSystemController(SignalSystem signalSystem) {
            DelayAdaptiveSignalController controller = new DelayAdaptiveSignalController(scenario, delayMonitor);

            /* add the responsive signal as a controler listener to be able to listen to
             * AfterMobsimEvents to save plans */
            // manager.addControlerListener(controller);

            return controller;
        }
    }

}
