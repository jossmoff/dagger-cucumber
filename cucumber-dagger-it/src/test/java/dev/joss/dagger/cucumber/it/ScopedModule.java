package dev.joss.dagger.cucumber.it;

import dagger.Module;
import dagger.Provides;
import dev.joss.dagger.cucumber.api.ScenarioScope;

/**
 * Scoped module covering per-scenario binding styles:
 *
 * <ul>
 *   <li>{@code @Provides @ScenarioScope} — unqualified scoped binding ({@link ScopedCounter},
 *       {@link ScopedService}).
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
}
