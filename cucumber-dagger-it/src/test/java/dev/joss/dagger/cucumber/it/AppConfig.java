package dev.joss.dagger.cucumber.it;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton configuration object. Each new instance is assigned a unique sequential id via a static
 * counter — if a second instance were ever created (i.e. the @Singleton scope were broken), the id
 * would differ from {@code 1} and tests would catch the regression.
 */
public final class AppConfig {

  private static final AtomicInteger COUNTER = new AtomicInteger();

  private final int id = COUNTER.incrementAndGet();

  AppConfig() {}

  /** Returns the unique creation-order id for this instance. The singleton always has id 1. */
  public int id() {
    return id;
  }
}
