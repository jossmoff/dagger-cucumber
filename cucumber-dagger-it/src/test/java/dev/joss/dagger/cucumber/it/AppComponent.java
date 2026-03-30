package dev.joss.dagger.cucumber.it;

import dagger.Component;
import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;
import javax.inject.Singleton;

/**
 * Root Dagger component for the integration-test suite.
 *
 * <p>The processor automatically includes the generated {@code CucumberDaggerModule} — only
 * application-specific modules need to be listed here. {@link PriceListModule} provides the {@link
 * PriceList} singleton that is injected into the per-scenario {@link Basket}.
 */
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class})
public interface AppComponent {}
