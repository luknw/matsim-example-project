package org.matsim.scripts;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;

import java.util.Random;

/**
 * Generates boring plans: Home -> Work -> Home
 * TODO: add malls and shops (restaurants at lunchtime/dinner ?) to some % of the plans
 */
public class PlanGenerator {
    private final PlacemarkSource homeSource;
    private final PlacemarkSource workSource;
    private final Random generator;

    PlanGenerator(PlacemarkSource homeSource, PlacemarkSource workSource) {
        this.homeSource = homeSource;
        this.workSource = workSource;
        this.generator = new Random();
    }

    public Plan generate(Population population) {
        Plan plan = population.getFactory().createPlan();
        Coord homeLocation = homeSource.getRandomPlacemark().getCoordInEpsg();
        Coord workLocation = workSource.getRandomPlacemark().getCoordInEpsg();

        int homeLeavingTime = (int) Math.round(generator.nextGaussian() * 60 * 60 + 7.5 * 60 * 60);
        plan.addActivity(createHome(population, homeLocation, homeLeavingTime));
        plan.addLeg(createDriveLeg(population));
        int workLeavingTime = (int) Math.round(generator.nextGaussian() * 20 * 60 + homeLeavingTime + 9 * 60 * 60);
        plan.addActivity(createWork(population, workLocation, workLeavingTime));
        plan.addLeg(createDriveLeg(population));
        plan.addActivity(createHome(population, homeLocation, Integer.MAX_VALUE));
        return plan;
    }

    private Activity createHome(Population population, Coord location, int endTmeInSeconds) {
        return createActivity(population, location, "home", endTmeInSeconds);
    }

    private Activity createWork(Population population, Coord location, int endTmeInSeconds) {
        return createActivity(population, location, "work", endTmeInSeconds);
    }

    private static Leg createDriveLeg(Population population) {
        return population.getFactory().createLeg(TransportMode.car);
    }

    private Activity createActivity(Population population, Coord location, String type, int endTmeInSeconds) {
        Activity activity = population.getFactory().createActivityFromCoord(type, location);
        activity.setEndTime(endTmeInSeconds);
        return activity;
    }


}
