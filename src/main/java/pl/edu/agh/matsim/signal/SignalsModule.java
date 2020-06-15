package pl.edu.agh.matsim.signal;


import com.google.inject.Singleton;
import org.matsim.core.controler.AbstractModule;
import pl.edu.agh.matsim.intensity.DensityMonitor;
import pl.edu.agh.matsim.intensity.IntensityMonitor;

/**
 * Define here which IntensityMonitor should be used in IntensityAdaptiveSignalController
 */
public class SignalsModule extends AbstractModule {

    @Override
    public void install() {
        bind(IntensityMonitor.class).to(DensityMonitor.class).in(Singleton.class);
    }
}
