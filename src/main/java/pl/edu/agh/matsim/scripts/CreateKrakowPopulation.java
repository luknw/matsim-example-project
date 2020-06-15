package pl.edu.agh.matsim.scripts;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;

/**
 * Inspired with tutorial.programming.example08DemandGeneration.RunPPopulationGenerator.
 * WARNING: simulation of population of 50,000 sims takes ~15/20 min (5000 about 3min)
 */
public class CreateKrakowPopulation {

    private static final int populationSize = 500; /* visualisation in Vim (free license) is limited to 500 agents */
    private static final String homeSourceFilePath = "./scenarios/krakow/home.csv";
    private static final String workSourceFilePath = "./scenarios/krakow/office.csv";
    private static final String supermarketSourceFilePath = "./scenarios/krakow/supermarket.csv";

    public static void main(String[] args) throws IOException {
        Population population = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation();
        generatePopulation(population);
        PopulationWriter populationWriter = new PopulationWriter(population);
        populationWriter.write("./scenarios/krakow/population.xml");
    }

    private static void generatePopulation(Population population) throws IOException {
        PlanGenerator planGenerator = getPlanGenerator();
        for (int i = 0; i < populationSize; i++) {
            Person person = population.getFactory().createPerson(Id.create("person_" + i, Person.class));
            Plan plan = planGenerator.generate(population);
            person.addPlan(plan);
            population.addPerson(person);
        }
    }

    private static PlanGenerator getPlanGenerator() throws IOException {
        PlacemarkSource homeSource = new PlacemarkSource(homeSourceFilePath);
        PlacemarkSource workSource = new PlacemarkSource(workSourceFilePath);
        PlacemarkSource supermarketSource = new PlacemarkSource(supermarketSourceFilePath);
        return new PlanGenerator(homeSource, workSource, supermarketSource);
    }
}
