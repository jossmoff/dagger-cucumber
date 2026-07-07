package dev.joss.dagger.cucumber.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.inject.Inject;

/**
 * Steps that verify {@code @ScenarioScope} isolation and singleton sharing.
 *
 * <p>Injects {@link ScopedCounter} (per-scenario), {@link AppConfig} (singleton), and {@link
 * ScopedService} (per-scenario, but holds a reference to the same singleton {@link AppConfig}).
 */
public final class ScopeSteps {

  private final ScopedCounter counter;
  private final AppConfig appConfig;
  private final ScopedService scopedService;

  @Inject
  public ScopeSteps(ScopedCounter counter, AppConfig appConfig, ScopedService scopedService) {
    this.counter = counter;
    this.appConfig = appConfig;
    this.scopedService = scopedService;
  }

  @When("I increment the scoped counter")
  public void iIncrementTheScopedCounter() {
    counter.increment();
  }

  @Then("the scoped counter is {int}")
  public void theScopedCounterIs(int expected) {
    assertThat(counter.get()).isEqualTo(expected);
  }

  @Then("the singleton app config has id {int}")
  public void theSingletonAppConfigHasId(int expectedId) {
    assertThat(appConfig.id()).isEqualTo(expectedId);
  }

  @Then("the singleton is shared between the root scope and scoped dependencies")
  public void theSingletonIsSharedBetweenRootAndScopedDependencies() {
    assertThat(appConfig).isSameAs(scopedService.appConfig());
  }
}
