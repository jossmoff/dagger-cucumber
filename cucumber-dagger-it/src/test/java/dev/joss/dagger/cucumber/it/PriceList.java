package dev.joss.dagger.cucumber.it;

import java.util.Locale;
import java.util.Map;

/**
 * Singleton price catalogue. Provided by {@link PriceListModule} and shared across all scenarios in
 * a test run.
 */
public final class PriceList {

  private final Map<String, Integer> prices;

  PriceList(Map<String, Integer> prices) {
    this.prices = prices;
  }

  /**
   * Returns the price of {@code item} in pence.
   *
   * @throws IllegalArgumentException if the item is not in the catalogue
   */
  public int priceOf(String item) {
    Integer price = prices.get(item.toLowerCase(Locale.ROOT));
    if (price == null) {
      throw new IllegalArgumentException("Unknown item: " + item);
    }
    return price;
  }
}
