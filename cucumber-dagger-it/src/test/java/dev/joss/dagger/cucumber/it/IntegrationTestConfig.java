package dev.joss.dagger.cucumber.it;

import dagger.BindsInstance;
import dagger.Component;
import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Root Dagger component for the integration-test suite.
 *
 * <p>The {@code @Component.Builder} demonstrates the {@code @BindsInstance} pattern. The {@code
 * storeName} setter binds the raw value under the {@code "rawStoreName"} qualifier. {@link
 * PriceListModule#provideStoreName} resolves it to a guaranteed non-null {@code "storeName"} value
 * with a {@code store.name} system-property fallback.
 *
 * <p>All {@code @BindsInstance} parameters are {@code @Nullable} so the runtime can call {@code
 * builder().build()} without setting any values (no-arg builder contract).
 */
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {

  @Component.Builder
  interface Builder {
    /**
     * Optionally overrides the store name shown on receipts. When {@code null}, {@link
     * PriceListModule#provideStoreName} falls back to {@code System.getProperty("store.name", "Test
     * Store")}.
     */
    @BindsInstance
    Builder storeName(@Named("rawStoreName") @Nullable String storeName);

    IntegrationTestConfig build();
  }
}
