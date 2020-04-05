package org.matsim.scripts;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Highly inspired with tutorial.programming.example08DemandGeneration.RunPPopulationGenerator.
 * Something is not consistent with coordinates (or Via can't visualize it properly) most likely EPSG:32634 should be used?
 */
public class CreateKrakowPopulation {

    private static final double minX = 19.6854;
    private static final double maxX = 20.3240;
    private static final double minY = 49.9265;
    private static final double maxY = 50.1668;
    private static final Map<String, Coord> zoneGeometries = new HashMap<>();
    private static final Random generator = new Random();

    public static void main(String[] args) {
        Population population = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation();
        fillZoneData();
        generatePopulation(population);
        PopulationWriter populationWriter = new PopulationWriter(population);
        populationWriter.write("./scenarios/krakow/population.xml");
    }

    private static void fillZoneData() {
        for (int i = 0; i < 10; i++) {
            zoneGeometries.put("home" + i, CoordUtils.createCoord(getRandomX(), getRandomY()));
            zoneGeometries.put("work" + i, CoordUtils.createCoord(getRandomX(), getRandomY()));
        }
    }

    private static double getRandomX() {
        return getRandomFromRange(minX, maxX);
    }

    private static double getRandomY() {
        return getRandomFromRange(minY, maxY);
    }

    private static double getRandomFromRange(double min, double max) {
        return generator.nextDouble() * (max - min) + min;
    }

    private static void generatePopulation(Population population) {
        for (int i = 0; i < 10; i++) {
            generateHomeWorkHomeTrips(population, "home" + i, "work" + i, 4);
        }
    }

    private static void generateHomeWorkHomeTrips(Population population, String from, String to, int quantity) {
        for (int i = 0; i < quantity; ++i) {
            Coord homeLocation = zoneGeometries.get(from);
            Coord workLocation = zoneGeometries.get(to);
            Person person = population.getFactory().createPerson(createId(from, to, i, TransportMode.car));
            Plan plan = population.getFactory().createPlan();
            plan.addActivity(createHome(population, homeLocation));
            plan.addLeg(createDriveLeg(population));
            plan.addActivity(createWork(population, workLocation));
            plan.addLeg(createDriveLeg(population));
            plan.addActivity(createHome(population, homeLocation));
            person.addPlan(plan);
            population.addPerson(person);
        }
    }

    private static Leg createDriveLeg(Population population) {
        return population.getFactory().createLeg(TransportMode.car);
    }

    private static Activity createWork(Population population, Coord workLocation) {
        Activity activity = population.getFactory().createActivityFromCoord("work", workLocation);
        activity.setEndTime(17 * 60 * 60);
        return activity;
    }

    private static Activity createHome(Population population, Coord homeLocation) {
        Activity activity = population.getFactory().createActivityFromCoord("home", homeLocation);
        activity.setEndTime(9 * 60 * 60);
        return activity;
    }

    private static Id<Person> createId(String source, String sink, int i, String transportMode) {
        return Id.create(transportMode + "_" + source + "_" + sink + "_" + i, Person.class);
    }
}
