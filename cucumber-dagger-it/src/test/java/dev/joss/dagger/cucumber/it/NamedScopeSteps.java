package dev.joss.dagger.cucumber.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Steps that verify {@code @Named}-qualified {@code @ScenarioScope} bindings.
 *
 * <p>Injects a {@code @Named("primary")} and {@code @Named("secondary")} {@link ScopedCounter}
 * alongside the regular (unqualified) {@link ScopedCounter} to confirm they are all independent
 * instances scoped to the same scenario.
 */
public final class NamedScopeSteps {

  private final ScopedCounter primaryCounter;
  private final ScopedCounter secondaryCounter;
  private final ScopedCounter regularCounter;

  @Inject
  public NamedScopeSteps(
      @Named("primary") ScopedCounter primaryCounter,
      @Named("secondary") ScopedCounter secondaryCounter,
      ScopedCounter regularCounter) {
    this.primaryCounter = primaryCounter;
    this.secondaryCounter = secondaryCounter;
    this.regularCounter = regularCounter;
  }

  @Then("the named primary counter starts at zero")
  public void theNamedPrimaryCounterStartsAtZero() {
    assertThat(primaryCounter.get()).isEqualTo(0);
  }

  @Then("the named primary counter is independent from the regular counter")
  public void theNamedPrimaryCounterIsIndependentFromTheRegularCounter() {
    primaryCounter.increment();
    assertThat(primaryCounter.get()).isEqualTo(1);
    assertThat(regularCounter.get()).isEqualTo(0);
  }

  @Then("two named counters of the same type are independent instances")
  public void twoNamedCountersOfTheSameTypeAreIndependentInstances() {
    primaryCounter.increment();
    primaryCounter.increment();
    assertThat(primaryCounter.get()).isEqualTo(2);
    assertThat(secondaryCounter.get()).isEqualTo(0);
  }
}
