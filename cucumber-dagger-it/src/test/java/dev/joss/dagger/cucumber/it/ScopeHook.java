package dev.joss.dagger.cucumber.it;

import io.cucumber.java.Before;
import jakarta.inject.Inject;

/**
 * Cucumber hook class that verifies hooks receive the same {@code @ScenarioScope} instances as
 * step-definition classes.
 *
 * <p>The {@code @Before("@hook-injection")} hook increments the shared {@link ScopedCounter} so
 * that {@code hook-injection.feature} can confirm the mutation is visible to subsequent step
 * definitions in the same scenario.
 */
public final class ScopeHook {

  private final ScopedCounter counter;

  @Inject
  public ScopeHook(ScopedCounter counter) {
    this.counter = counter;
  }

  @Before("@hook-injection")
  public void beforeHookInjection() {
    counter.increment();
  }
}
