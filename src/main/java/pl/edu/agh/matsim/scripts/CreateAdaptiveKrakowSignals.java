package pl.edu.agh.matsim.scripts;

import com.opencsv.CSVWriter;
import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.xml.dynsax.OsmXmlReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.controller.fixedTime.DefaultPlanbasedSignalSystemController;
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerSignalController;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsScenarioWriter;
import org.matsim.contrib.signals.data.signalgroups.v20.*;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.utils.SignalUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.SearchableNetwork;
import org.matsim.core.network.algorithms.intersectionSimplifier.DensityCluster;
import org.matsim.core.network.algorithms.intersectionSimplifier.containers.Cluster;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import pl.edu.agh.matsim.signal.IntensityAdaptiveSignalController;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateAdaptiveKrakowSignals {

    /**
     * Radius 150 is enough to cluster big junctions like Mogilskie
     */
    private static final double SIGNAL_SYSTEM_CLUSTERING_RADIUS = 150.0;

    private static void populateSignalsData(SignalsData signalsData, List<Cluster> osmSignalSystems) {
        SignalSystemsData systems = signalsData.getSignalSystemsData();
        SignalGroupsData groups = signalsData.getSignalGroupsData();
        SignalControlData control = signalsData.getSignalControlData();

        osmSignalSystems.forEach(sys -> {
            SignalSystemData sysData = systems.getFactory().createSignalSystemData(Id.create(sys.getId(), SignalSystem.class));
            systems.addSignalSystemData(sysData);

            sys.getPoints().forEach(signal -> {
                SignalData signalData = systems.getFactory().createSignalData(Id.create(signal.getNode().getId(), Signal.class));
                sysData.addSignalData(signalData);
                signal.getNode().getInLinks().values().forEach(in -> signalData.setLinkId(in.getId()));
            });

            SignalUtils.createAndAddSignalGroups4Signals(groups, sysData);

            /* choose signal system controller */
            // addPlanbasedSignalSystemController(control, sysData, groups, 55);
            // addAdaptiveSignalSystemController(control, sysData, groups, 25);
             addLaemmerSignalController(control, sysData);
        });
    }

    private static void addLaemmerSignalController(SignalControlData control, SignalSystemData sysData) {
        SignalSystemControllerData controller = control.getFactory().createSignalSystemControllerData(sysData.getId());
        control.addSignalSystemControllerData(controller);
        controller.setControllerIdentifier(LaemmerSignalController.IDENTIFIER);
    }

    private static void addPlanbasedSignalSystemController(SignalControlData control, SignalSystemData sysData, SignalGroupsData groups, int greenLength) {
        SignalSystemControllerData controller = control.getFactory().createSignalSystemControllerData(sysData.getId());
        control.addSignalSystemControllerData(controller);
        controller.setControllerIdentifier(DefaultPlanbasedSignalSystemController.IDENTIFIER);

        SignalPlanData plan = control.getFactory().createSignalPlanData(Id.create(0, SignalPlan.class));
        controller.addSignalPlanData(plan);
        /* since there is a single plan for each group startTime and endTime are set to 0.0 */
        plan.setStartTime(0.0);
        plan.setEndTime(0.0);
        plan.setOffset(0);

        int cycleTime = createSignalGroupsSettings(control, sysData, groups, plan, greenLength);
        plan.setCycleTime(cycleTime);
    }

    /**
     * Creates a plan for each group in the system
     *
     * @return cycle time
     */
    private static int createSignalGroupsSettings(SignalControlData control, SignalSystemData sysData, SignalGroupsData groups, SignalPlanData plan, int greenLength) {
        int startTime = 0;
        /* Currently, it is sufficient to iterate over sys's signals since each signal has separate group with the same id
         *  Once we have smart groups current sysData's groups should be used instead
         * */
        for(SignalData signalData: sysData.getSignalData().values()) {
            Id<SignalGroup> signalGroupId = groups.getSignalGroupDataBySystemId(sysData.getId()).get(signalData.getId()).getId();
            SignalGroupSettingsData settings = control.getFactory().createSignalGroupSettingsData(signalGroupId);
            settings.setOnset(startTime);
            settings.setDropping(startTime + greenLength);
            plan.addSignalGroupSettings(settings);
            startTime += greenLength + 5;
        }
        return startTime;
    }

    private static void addAdaptiveSignalSystemController(SignalControlData control, SignalSystemData sysData, SignalGroupsData groups, int greenLength) {
        SignalSystemControllerData controller = control.getFactory().createSignalSystemControllerData(sysData.getId());
        control.addSignalSystemControllerData(controller);
        controller.setControllerIdentifier(IntensityAdaptiveSignalController.IDENTIFIER);

        /* add separate plan for every 5 cycles interval */
        int cyclesPerPlan = 5;
        int planStartTime = 0;
        while(planStartTime < 24*3600){
            SignalPlanData plan = control.getFactory().createSignalPlanData(Id.create(planStartTime, SignalPlan.class));
            // add the plan to the system control
            controller.addSignalPlanData(plan);

            plan.setStartTime((double) planStartTime);
            plan.setOffset(0);

            int cycleTime = createSignalGroupsSettings(control, sysData, groups, plan, greenLength);
            plan.setCycleTime(cycleTime);
            plan.setEndTime((double) planStartTime + cyclesPerPlan * cycleTime);
            planStartTime += plan.getEndTime();
        }
    }

    public static void main(String[] args) throws IOException, OsmInputException {
        List<Cluster> signalSystems = readSignalSystems();

        saveSignalSystemsCsv(signalSystems);

        Scenario dummyScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        SignalsData signalsData = createSignalsData(dummyScenario);
        populateSignalsData(signalsData, signalSystems);

        saveSignalsData(dummyScenario);
    }

    private static void saveSignalSystemsCsv(List<Cluster> signalSystems) throws IOException {
        try (BufferedWriter out = IOUtils.getBufferedWriter("./scenarios/krakow/krakow_signal_systems.csv");
             CSVWriter csv = new CSVWriter(out)) {
            csv.writeNext(new String[]{"signalSystemId", "x", "y"});
            signalSystems.forEach(s -> {
                Coord centre = s.getCenterOfGravity();
                csv.writeNext(new String[]{s.getId().toString(), String.valueOf(centre.getX()), String.valueOf(centre.getY())});
            });
        }
    }

    private static void saveSignalsData(Scenario scenario) {
        SignalsScenarioWriter signalsWriter = new SignalsScenarioWriter();
        signalsWriter.setSignalSystemsOutputFilename("./scenarios/krakow/signal_systems.xml");
        signalsWriter.setSignalGroupsOutputFilename("./scenarios/krakow/signal_groups.xml");
        signalsWriter.setSignalControlOutputFilename("./scenarios/krakow/signal_control.xml");
        signalsWriter.writeSignalsData(scenario);
    }

    private static SignalsData createSignalsData(Scenario scenario) {
        SignalSystemsConfigGroup signalSystemsConfigGroup =
                ConfigUtils.addOrGetModule(scenario.getConfig(), SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class);
        signalSystemsConfigGroup.setUseSignalSystems(true);

        SignalsData signalsData = SignalUtils.createSignalsData(signalSystemsConfigGroup);
        scenario.addScenarioElement(SignalsData.ELEMENT_NAME, signalsData);

        return signalsData;
    }

    private static List<Cluster> readSignalSystems() throws FileNotFoundException, OsmInputException {
        Set<Node> crossings = new HashSet<>();

        Network network = NetworkUtils.readNetwork("./scenarios/krakow/network.xml");
        CoordinateTransformation transform = TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:32634");

        OsmXmlReader signalReader = new OsmXmlReader("./scenarios/krakow/krakow_traffic_signals.osm", true);
        signalReader.setHandler(new DefaultOsmHandler() {
            @Override
            public void handle(OsmNode osmNode) {
                Coord signalCoord = transform.transform(new Coord(osmNode.getLongitude(), osmNode.getLatitude()));
                Node nearestNode = ((SearchableNetwork) network).getNearestNode(signalCoord);
                crossings.add(nearestNode);
            }
        });
        signalReader.read();

        DensityCluster signalSystemClusterer = new DensityCluster(new ArrayList<>(crossings), false);
        signalSystemClusterer.clusterInput(SIGNAL_SYSTEM_CLUSTERING_RADIUS, 1);
        return signalSystemClusterer.getClusterList();
    }

//    private void networkNodesToSignalSystem() {
//        QuadTree<ClusterActivity> signalSystemCentres = signalSystemClusterer.getClusteredPoints();
//
//        Map<Id<Node>, List<Node>> signalSystems = new HashMap<>();
//        network.getNodes().values().forEach(n -> {
//            Coord nodeCoord = n.getCoord();
//            Node closestSignalSystem = signalSystemCentres.getClosest(nodeCoord.getX(), nodeCoord.getY()).getNode();
//            double distanceToSystem = CoordUtils.calcEuclideanDistance(closestSignalSystem.getCoord(), nodeCoord);
//            if (distanceToSystem <= SIGNAL_SYSTEM_CLUSTERING_RADIUS) {
//                signalSystems.computeIfAbsent(closestSignalSystem.getId(), ignore -> new ArrayList<>()).add(n);
//            }
//        });
//    }
}
