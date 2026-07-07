package dev.joss.dagger.cucumber.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Steps that verify {@code @Named}-qualified {@code @ScenarioScope} bindings.
 *
 * <p>Injects a {@code @Named("primary")} {@link ScopedCounter} alongside the regular (unqualified)
 * {@link ScopedCounter} to confirm they are independent instances scoped to the same scenario.
 */
public final class NamedScopeSteps {

  private final ScopedCounter primaryCounter;
  private final ScopedCounter regularCounter;

  @Inject
  public NamedScopeSteps(
      @Named("primary") ScopedCounter primaryCounter, ScopedCounter regularCounter) {
    this.primaryCounter = primaryCounter;
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
}
