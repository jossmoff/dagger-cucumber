package dev.joss.dagger.cucumber.it;

import io.cucumber.java.en.When;
import jakarta.inject.Inject;

/**
 * A second step-definition class that injects the same {@link ScopedCounter} as {@link ScopeSteps}.
 * Used by {@code cross-scope-sharing.feature} to verify that Dagger returns the identical
 * {@code @ScenarioScope} instance to every step-def class within a single scenario.
 */
public final class SecondaryScopeSteps {

  private final ScopedCounter counter;

  @Inject
  public SecondaryScopeSteps(ScopedCounter counter) {
    this.counter = counter;
  }

  @When("I increment the scoped counter from the secondary steps")
  public void iIncrementTheScopedCounterFromTheSecondarySteps() {
    counter.increment();
  }
}
