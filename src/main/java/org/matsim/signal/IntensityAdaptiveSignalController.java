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
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.model.*;
import org.matsim.core.controler.ControlerListenerManager;
import org.matsim.intensity.DelayMonitor;
import org.matsim.intensity.IntensityMonitor;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Based on SimpleResponsiveSignal from org.matsim.codeexamples.simpleResponsiveSignalEngine.
 * Assumes that every junction has 24h / ADAPTATION_INTERVAL plans for every ADAPTATION_INTERVAL so it can analyze traffic and adapt discretely
 * upcoming plans
 * //TODO change DelayMonitor or use DensityMonitor instead - could be even better?
 */
public class DelayAdaptiveSignalController extends AbstractSignalController {

    private static final Logger LOG = Logger.getLogger(DelayAdaptiveSignalController.class);
    public static final String IDENTIFIER = "DelayAdaptiveSignalControl";

    public static final int ADAPTATION_INTERVAL = 10 * 60; /* adapt plans every ADAPTATION_INTERVAL */
    private static final int MAX_SHIFT = 15;

    private final IntensityMonitor delayMonitor;
    private final Scenario scenario;
    private int lastUpdateMarker; /* calculated as time of the last plan update divided by ADAPTATION_INTERVAL (floor) */

    private SignalController planBasedSignalController;
    private LinkedList<SignalPlan> planQueue; /* planBasedSignalController's planQueue */

    private DelayAdaptiveSignalController(Scenario scenario, IntensityMonitor delayMonitor, SignalController planBasedSignalController) {
        super();
        this.scenario = scenario;
        this.delayMonitor = delayMonitor;
        this.planBasedSignalController = planBasedSignalController;
        this.lastUpdateMarker = 0;
    }

    /**
     * updates current and next plan according to the delays on links if necessary
     */
    private void updateSignalsIfNecessary(double updateTime) {

        Map<SignalGroup, Double> signalGroupToDelayOnLinks = getSignalGroupToDelayOnLinks(updateTime);

        if (delaysExist(signalGroupToDelayOnLinks)) { /* improve plans only if delays exist */
            Map<Id<SignalPlan>, SignalPlanData> signalPlanData = getSignalPlanData();
            SignalPlanData current = signalPlanData.get(planQueue.getLast().getId());
            updatePlan(current, planQueue.size()-1, signalGroupToDelayOnLinks);
            SignalPlanData next = signalPlanData.get(planQueue.getFirst().getId());
            updatePlan(next, 0, signalGroupToDelayOnLinks);
        } else {
            /* do nothing */
            LOG.info("Signal control unchanged.");
        }
    }

    private boolean delaysExist(Map<SignalGroup, Double> signalGroupToDelayOnLinks){
        return signalGroupToDelayOnLinks.values().stream().anyMatch(delay -> delay > 0.0);
    }

    /**
     * updates plan according to the delays on links
     */
    private void updatePlan(SignalPlanData planData, int indexInTheQueue, Map<SignalGroup, Double> signalGroupToDelayOnLinks) {
        SortedMap<Id<SignalGroup>, SignalGroupSettingsData> signalGroupSettings = planData.getSignalGroupSettingsDataByGroupId();
        double avgDelay = signalGroupToDelayOnLinks.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxDelay = signalGroupToDelayOnLinks.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if(maxDelay == avgDelay) {
            return; /* nothing we can do */
        }
        signalGroupSettings.forEach((id, settingsData) -> {
            double greenTimeShiftFactor  = (signalGroupToDelayOnLinks.get(id) - avgDelay)/(maxDelay- avgDelay);
            int greenTimeShift = (int) Math.round(greenTimeShiftFactor * MAX_SHIFT);
            settingsData.setDropping(settingsData.getDropping() + greenTimeShift);
            settingsData.setOnset(settingsData.getDropping() - greenTimeShift);
            LOG.info(id+": onset " + settingsData.getOnset() + ", dropping " + settingsData.getDropping());
        });

        //TODO adjust cycle time

        /* the new plan needs to be added to the signal control since it was made persistent over the iterations
        * and is not built newly each iteration from the data. theresa, jan'20 */
        SignalPlan signalPlan = new DatabasedSignalPlan(planData);
        planQueue.set(indexInTheQueue, signalPlan);
        addPlan(signalPlan);
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
        return delayMonitor.getAverageIntensityForLinkInInterval(link, updateTime - ADAPTATION_INTERVAL, updateTime);
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
        planBasedSignalController.setSignalSystem(signalSystem);
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
        IntensityMonitor delayMonitor;

        @Override
        public SignalController createSignalSystemController(SignalSystem signalSystem) {
            SignalController planBasedSignalController = new FixedTimeFactory().createSignalSystemController(signalSystem);
            SignalController controller = new DelayAdaptiveSignalController(scenario, delayMonitor, planBasedSignalController);
            controller.setSignalSystem(signalSystem);
            return controller;
        }
    }

}
