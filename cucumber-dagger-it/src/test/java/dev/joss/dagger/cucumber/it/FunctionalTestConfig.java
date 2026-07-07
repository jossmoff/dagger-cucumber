package dev.joss.dagger.cucumber.it;

import dagger.Component;
import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;
import jakarta.inject.Singleton;

/**
 * Root Dagger component for the functional integration-test suite. Each module targets a distinct
 * set of library behaviours under test.
 */
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {AppModule.class, ScopedModule.class})
public interface FunctionalTestConfig {}
