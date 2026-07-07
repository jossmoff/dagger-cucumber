package dev.joss.dagger.cucumber.it;

import dagger.Module;
import dagger.Provides;
import dev.joss.dagger.cucumber.api.ScenarioScope;
import jakarta.inject.Named;

/**
 * Scoped module covering per-scenario binding styles:
 *
 * <ul>
 *   <li>{@code @Provides @ScenarioScope} — unqualified scoped binding ({@link ScopedCounter},
 *       {@link ScopedService}).
 *   <li>{@code @Provides @ScenarioScope @Named} — named (qualified) scoped binding ({@link
 *       ScopedCounter} with {@code "primary"} qualifier).
 * </ul>
 */
@Module
public final class ScopedModule {

  @Provides
  @ScenarioScope
  static ScopedCounter provideScopedCounter() {
    return new ScopedCounter();
  }

  @Provides
  @ScenarioScope
  static ScopedService provideScopedService(AppConfig appConfig) {
    return new ScopedService(appConfig);
  }

  @Provides
  @ScenarioScope
  @Named("primary")
  static ScopedCounter providePrimaryScopedCounter() {
    return new ScopedCounter();
  }
}
