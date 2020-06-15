package pl.edu.agh.matsim.signal;

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
import pl.edu.agh.matsim.intensity.IntensityMonitor;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Based on SimpleResponsiveSignal from org.matsim.codeexamples.simpleResponsiveSignalEngine.
 * Assumes that every junction has 24h / interval plans for every interval, so it can analyze traffic and adapt discretely
 * plans.
 */
public class IntensityAdaptiveSignalController extends AbstractSignalController {

    private static final Logger LOG = Logger.getLogger(IntensityAdaptiveSignalController.class);
    public static final String IDENTIFIER = "IntensityAdaptiveSignalControl";

    private int interval; /* adapt plans every interval = 5 * cycle */

    private final IntensityMonitor intensityMonitor;
    private final Scenario scenario;
    private int lastUpdateMarker; /* calculated as time of the last plan update divided by an interval (floor) */

    private final SignalController planBasedSignalController;
    private LinkedList<SignalPlan> planQueue; /* planBasedSignalController's planQueue */

    private IntensityAdaptiveSignalController(Scenario scenario, IntensityMonitor intensityMonitor, SignalController planBasedSignalController) {
        super();
        this.scenario = scenario;
        this.intensityMonitor = intensityMonitor;
        this.planBasedSignalController = planBasedSignalController;
        this.lastUpdateMarker = 0;
    }

    /**
     * updates current and next plan according to the intensity on links if necessary
     */
    private void updateSignalsIfNecessary(double updateTime) {

        Map<Id<SignalGroup>, Double> signalGroupToIntensityOnLinks = getSignalGroupToIntensityOnLinks(updateTime);

        if (trafficTooIntense(signalGroupToIntensityOnLinks)) { /* improve plans only if traffic too intense on any link */
            Map<Id<SignalPlan>, SignalPlanData> signalPlanData = getSignalPlanData();
            SignalPlanData current = signalPlanData.get(planQueue.getLast().getId());
            updatePlan(current, planQueue.size()-1, signalGroupToIntensityOnLinks);
            SignalPlanData next = signalPlanData.get(planQueue.getFirst().getId());
            updatePlan(next, 0, signalGroupToIntensityOnLinks);
        } /* else do nothing */

    }

    private boolean trafficTooIntense(Map<Id<SignalGroup>, Double> signalGroupToIntensityOnLinks){
        return signalGroupToIntensityOnLinks.values().stream().anyMatch(intensity -> intensity > intensityMonitor.getIntensityThreshold());
    }

    /**
     * updates plan according to the intensity on links
     */
    private void updatePlan(SignalPlanData planData, int indexInTheQueue, Map<Id<SignalGroup>, Double> signalGroupToIntensityOnLinks) {
        SortedMap<Id<SignalGroup>, SignalGroupSettingsData> signalGroupSettings = planData.getSignalGroupSettingsDataByGroupId();

        double median = getMedian(signalGroupToIntensityOnLinks.values());
        int startTime = 0;
        for( Map.Entry<Id<SignalGroup>, SignalGroupSettingsData> entry  : signalGroupSettings.entrySet()) { //TODO most likely should be sorted
            SignalGroupSettingsData settingsData = entry.getValue();
            int greenDuration = settingsData.getDropping() - settingsData.getOnset();
            double linkIntensity = signalGroupToIntensityOnLinks.get(entry.getKey());
            int durationChange = Double.compare(linkIntensity, median) * 5;
            greenDuration = (greenDuration + durationChange) >= 5 ? (greenDuration + durationChange) : greenDuration;

            settingsData.setOnset(startTime);
            settingsData.setDropping(startTime + greenDuration);
            startTime += greenDuration + 5;
            LOG.warn(entry.getKey()+": onset " + settingsData.getOnset() + ", dropping " + settingsData.getDropping());
        }
        planData.setCycleTime(startTime);

        /* the new plan needs to be added to the signal control since it was made persistent over the iterations
        * and is not built newly each iteration from the data. theresa, jan'20 */
        SignalPlan signalPlan = new DatabasedSignalPlan(planData);
        planQueue.set(indexInTheQueue, signalPlan);
        addPlan(signalPlan);
    }

    private Map<Id<SignalGroup>, Double> getSignalGroupToIntensityOnLinks(double updateTime) {
        return system.getSignalGroups().values().stream()
                .collect(Collectors.toMap(SignalGroup::getId,
                        signalGroup -> signalGroup.getSignals().values().stream()
                                .map(signal -> getIntensityOnSignalsLink(signal, updateTime))
                                .mapToDouble(Double::doubleValue).sum()));
    }

    private double getIntensityOnSignalsLink(Signal signal, double updateTime) {
        Link link = scenario.getNetwork().getLinks().get(signal.getLinkId());
        return intensityMonitor.getAverageIntensityForLinkInInterval(link.getId(), updateTime - interval, updateTime);
    }

    private Map<Id<SignalPlan>, SignalPlanData> getSignalPlanData() {
        SignalsData signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
        SignalControlData signalControl = signalsData.getSignalControlData();
        SignalSystemControllerData signalControlSystem = signalControl.getSignalSystemControllerDataBySystemId().get(system.getId());
        return signalControlSystem.getSignalPlanData();
    }

    private double getMedian(Collection<Double> doubles) {
        int size = doubles.size();

        return size%2 == 0 ?
                doubles.stream().sorted()
                        .skip(size/2-1)
                        .limit(2)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0) :
                doubles.stream().sorted()
                        .skip(size/2)
                        .findFirst()
                        .orElse(0.0);
    }

    @Override
    public void setSignalSystem(SignalSystem signalSystem) {
        super.setSignalSystem(signalSystem);
        planBasedSignalController.setSignalSystem(signalSystem);
    }

    @Override
    public void addPlan(SignalPlan plan) {
        interval = plan.getCycleTime() * 10;
        super.addPlan(plan);
        planBasedSignalController.addPlan(plan);
    }

    @Override
    public void updateState(double timeSeconds) {
        /* adapt plans every interval seconds */
        if (planShouldBeAdapted(timeSeconds)) {
            lastUpdateMarker++;
            updateSignalsIfNecessary(timeSeconds);
        }
        planBasedSignalController.updateState(timeSeconds);
    }

    private boolean planShouldBeAdapted(double timeSeconds) {
        return Math.floor(timeSeconds / interval) > lastUpdateMarker;
    }

    @Override
    public void simulationInitialized(double simStartTimeSeconds) {
        planBasedSignalController.simulationInitialized(simStartTimeSeconds);
        /* very ugly and hacky but an alternative is to copy the sourcecode of DefaultPlanbasedSignalSystemController  */
        try {
            Field planQueueField;
            planQueueField = DefaultPlanbasedSignalSystemController.class.getDeclaredField("planQueue");
            planQueueField.setAccessible(true);
            planQueue = (LinkedList<SignalPlan>) planQueueField.get(planBasedSignalController);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public final static class AdaptiveSignalControllerFactory implements SignalControllerFactory {

        @Inject
        Scenario scenario;
        @Inject
        IntensityMonitor intensityMonitor;

        @Override
        public SignalController createSignalSystemController(SignalSystem signalSystem) {

            SignalController planBasedSignalController = new FixedTimeFactory().createSignalSystemController(signalSystem);
            SignalController controller = new IntensityAdaptiveSignalController(scenario, intensityMonitor, planBasedSignalController);
            controller.setSignalSystem(signalSystem);
            return controller;
        }
    }

}
