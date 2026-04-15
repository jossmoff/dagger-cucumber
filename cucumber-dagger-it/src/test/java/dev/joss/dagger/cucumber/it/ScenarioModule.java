package dev.joss.dagger.cucumber.it;

import dagger.Module;
import dagger.Provides;
import dev.joss.dagger.cucumber.api.ScenarioScope;

/**
 * Dagger module that provides per-scenario bindings for the integration-test suite.
 *
 * <p>Each {@code @ScenarioScope} method is called once per scenario; Dagger caches the result for
 * the lifetime of the {@code GeneratedScopedComponent}.
 */
@Module
public final class ScenarioModule {

  @Provides
  @ScenarioScope
  static Discount provideDiscount() {
    return new Discount();
  }

  @Provides
  @ScenarioScope
  static Basket provideBasket(PriceList priceList, Discount discount) {
    return new Basket(priceList, discount);
  }
}
