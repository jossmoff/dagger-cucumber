package dev.joss.dagger.cucumber.it;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cucumber hooks that emit per-scenario and cumulative wall-clock timing to stdout.
 *
 * <p>Compare the cumulative total from {@code ./gradlew :cucumber-dagger-it:test --rerun-tasks}
 * against {@code ./gradlew :cucumber-spring-it:test --rerun-tasks} to gauge relative overhead.
 */
public final class TimingHooks {

  private static final AtomicLong totalNanos = new AtomicLong();
  private static final AtomicInteger scenarioCount = new AtomicInteger();

  private long scenarioStartNanos;

  @Inject
  public TimingHooks() {}

  @Before
  public void beforeScenario(Scenario _scenario) {
    scenarioStartNanos = System.nanoTime();
  }

  @After
  public void afterScenario(Scenario scenario) {
    long elapsedNanos = System.nanoTime() - scenarioStartNanos;
    long cumulativeNanos = totalNanos.addAndGet(elapsedNanos);
    int count = scenarioCount.incrementAndGet();
    System.out.printf(
        "[dagger-it] '%s': %.2f ms  (cumulative after %d scenarios: %.2f ms)%n",
        scenario.getName(), elapsedNanos / 1_000_000.0, count, cumulativeNanos / 1_000_000.0);
  }
}
