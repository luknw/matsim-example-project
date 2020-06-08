package pl.edu.agh.matsim.scripts;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;

import java.util.Random;

/**
 * Generates boring plans: Home -> Work -> Home (in 30% of cases Home -> Work -> Supermarket -> Home)
 */
public class PlanGenerator {
    private final PlacemarkSource homeSource;
    private final PlacemarkSource workSource;
    private final PlacemarkSource supermarketSource;
    private final Random generator;

    PlanGenerator(PlacemarkSource homeSource, PlacemarkSource workSource, PlacemarkSource supermarketSource) {
        this.homeSource = homeSource;
        this.workSource = workSource;
        this.supermarketSource = supermarketSource;
        this.generator = new Random();
    }

    public Plan generate(Population population) {
        Plan plan = population.getFactory().createPlan();
        Coord homeLocation = homeSource.getRandomPlacemark().getCoordInEpsg();
        Coord workLocation = workSource.getRandomPlacemark().getCoordInEpsg();
        Coord supermarketLocation = supermarketSource.getRandomPlacemark().getCoordInEpsg();

        int homeLeavingTime = (int) Math.round(generator.nextGaussian() * 60 * 60 + 7.5 * 60 * 60);
        plan.addActivity(createHome(population, homeLocation, homeLeavingTime));
        plan.addLeg(createDriveLeg(population));

        int workLeavingTime = (int) Math.round(generator.nextGaussian() * 20 * 60 + homeLeavingTime + 9 * 60 * 60);
        plan.addActivity(createWork(population, workLocation, workLeavingTime));
        plan.addLeg(createDriveLeg(population));

        /* ~30% of the population goes for groceries after work */
        if(generator.nextInt(3) % 3 == 0) {
            int supermarketLeavingTime = (int) Math.round(generator.nextGaussian() * 10 * 60 + workLeavingTime + 60 * 60);
            plan.addActivity(createShop(population, supermarketLocation, supermarketLeavingTime));
            plan.addLeg(createDriveLeg(population));
        }

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
