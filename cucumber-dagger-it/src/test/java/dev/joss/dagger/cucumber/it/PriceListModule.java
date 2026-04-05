package dev.joss.dagger.cucumber.it;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * User-defined root module that provides a {@link PriceList} singleton. Listed in {@link
 * AppComponent}'s {@code modules} attribute; the generated wrapper adds {@code CucumberDaggerModule}
 * automatically.
 */
@Module
public final class PriceListModule {

  @Provides
  @Singleton
  static PriceList providePriceList() {
    return new PriceList(Map.of("apple", 30, "banana", 15, "cherry", 50));
  }
}
