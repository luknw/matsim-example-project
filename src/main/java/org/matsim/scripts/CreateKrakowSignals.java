package org.matsim.scripts;

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
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerSignalController;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsScenarioWriter;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.model.Signal;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateKrakowSignals {

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
                SignalData signalData = systems.getFactory().createSignalData(Id.create(signal.getId(), Signal.class));
                sysData.addSignalData(signalData);
                signal.getNode().getInLinks().values().forEach(in -> signalData.setLinkId(in.getId()));
            });

            SignalUtils.createAndAddSignalGroups4Signals(groups, sysData);

            SignalSystemControllerData controller = control.getFactory().createSignalSystemControllerData(sysData.getId());
            control.addSignalSystemControllerData(controller);

            controller.setControllerIdentifier(LaemmerSignalController.IDENTIFIER);
        });
    }

    public static void main(String[] args) throws IOException, OsmInputException {
        List<Cluster> signalSystems = readSignalSystems();
        Scenario dummyScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        SignalsData signalsData = createSignalsData(dummyScenario);
        populateSignalsData(signalsData, signalSystems);
        saveSignalsData(dummyScenario);
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
