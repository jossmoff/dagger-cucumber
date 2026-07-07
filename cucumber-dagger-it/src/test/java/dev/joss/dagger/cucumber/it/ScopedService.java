package dev.joss.dagger.cucumber.it;

/**
 * A per-scenario service that holds a reference to the singleton {@link AppConfig}. Used to verify
 * that the same singleton instance is shared between a directly-injected binding and a
 * scoped-dependency binding within the same scenario.
 */
public final class ScopedService {

  private final AppConfig appConfig;

  ScopedService(AppConfig appConfig) {
    this.appConfig = appConfig;
  }

  /** Returns the {@link AppConfig} that was injected into this scoped service. */
  public AppConfig appConfig() {
    return appConfig;
  }
}
