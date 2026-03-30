package dev.joss.dagger.cucumber.it;

import dagger.Component;
import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;
import javax.inject.Singleton;

/**
 * Root Dagger component for the integration-test suite.
 *
 * <p>Includes two modules beyond the generated {@code CucumberDaggerModule}:
 *
 * <ul>
 *   <li>{@code CucumberDaggerModule} — generated; wires the per-scenario subcomponent into this
 *       component (must always be present).
 *   <li>{@link PriceListModule} — user-defined; provides the {@link PriceList} singleton that is
 *       injected into the per-scenario {@link Basket}.
 * </ul>
 */
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {CucumberDaggerModule.class, PriceListModule.class})
public interface AppComponent extends CucumberDaggerComponent {}
