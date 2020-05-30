package org.matsim.scripts;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Generates boring plans: Home -> Work -> Home (in 30% of cases Home -> Work -> Supermarket -> Home)
 */
public class PlanGenerator {
    private final PlacemarkSource homeSource;
    private final PlacemarkSource workSource;
    private final PlacemarkSource supermarketSource;
    private final Random generator;

    int i = 0;
    List<Coord> homes = Arrays.asList(new Coord(423321.8999744049, 5545419.05883264),
            new Coord(423182.97427784745, 5545407.062539328),
            new Coord(423127.27545815904, 5545257.324252227),
            new Coord(423250.9990289205, 5545271.405533751));
    List<Coord> works = Arrays.asList(new Coord(423127.27545815904, 5545257.324252227),
            new Coord(423250.9990289205, 5545271.405533751),
            new Coord(423297.178059199, 5545368.147721792),
            new Coord(423208.189120309, 5545409.45837616));

    PlanGenerator(PlacemarkSource homeSource, PlacemarkSource workSource, PlacemarkSource supermarketSource) {
        this.homeSource = homeSource;
        this.workSource = workSource;
        this.supermarketSource = supermarketSource;
        this.generator = new Random();
    }

    public Plan generate(Population population) {
        int i = this.i++ % homes.size();
        Plan plan = population.getFactory().createPlan();

        Coord homeLocation = homes.get(i); // homeSource.getRandomPlacemark().getCoordInEpsg();
        Coord workLocation = works.get(i); // workSource.getRandomPlacemark().getCoordInEpsg();
        Coord supermarketLocation = supermarketSource.getRandomPlacemark().getCoordInEpsg();

        int homeLeavingTime = (int) Math.round(7.5 * 60 * 60);
        plan.addActivity(createHome(population, homeLocation, homeLeavingTime));
        plan.addLeg(createDriveLeg(population));

        int workLeavingTime = (int) Math.round(homeLeavingTime + 9 * 60 * 60);
        plan.addActivity(createWork(population, workLocation, workLeavingTime));
        plan.addLeg(createDriveLeg(population));

//        /* ~30% of the population goes for groceries after work */
//        if(generator.nextInt(3) % 3 == 0) {
//            int supermarketLeavingTime = (int) Math.round(workLeavingTime + 60 * 60);
//            plan.addActivity(createShop(population, supermarketLocation, supermarketLeavingTime));
//            plan.addLeg(createDriveLeg(population));
//        }

        /* it's agent's last activity so endTime is set to MAX_VALUE */
        plan.addActivity(createHome(population, homeLocation, Integer.MAX_VALUE));
        return plan;
    }

    private Activity createHome(Population population, Coord location, int endTmeInSeconds) {
        return createActivity(population, location, "home", endTmeInSeconds);
    }

    private Activity createWork(Population population, Coord location, int endTmeInSeconds) {
        return createActivity(population, location, "work", endTmeInSeconds);
    }

    private Activity createShop(Population population, Coord location, int endTmeInSeconds) {
        return createActivity(population, location, "shop", endTmeInSeconds);
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
