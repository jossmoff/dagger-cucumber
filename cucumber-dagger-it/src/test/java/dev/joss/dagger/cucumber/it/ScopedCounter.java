package dev.joss.dagger.cucumber.it;

/**
 * A mutable counter with per-scenario lifetime. Provided via {@link
 * ScopedModule#provideScopedCounter()}; a fresh instance starting at zero is created for every
 * scenario.
 */
public final class ScopedCounter {

  private int value = 0;

  ScopedCounter() {}

  /** Increments the counter by one. */
  public void increment() {
    value++;
  }

  /** Returns the current counter value. */
  public int get() {
    return value;
  }
}
