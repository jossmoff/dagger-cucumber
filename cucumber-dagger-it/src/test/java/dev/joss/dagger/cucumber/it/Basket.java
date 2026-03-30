package dev.joss.dagger.cucumber.it;

import dev.joss.dagger.cucumber.api.CucumberScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-scenario shopping basket (Style-A {@code @CucumberScoped}). A fresh instance is injected for
 * every scenario.
 *
 * <p>Receives the singleton {@link PriceList} provided by {@link PriceListModule} and the
 * per-scenario {@link Discount} (also Style-A {@code @CucumberScoped}).
 */
@CucumberScoped
public final class Basket {

  private final PriceList priceList;
  private final Discount discount;
  private final List<String> items = new ArrayList<>();

  @Inject
  public Basket(PriceList priceList, Discount discount) {
    this.priceList = priceList;
    this.discount = discount;
  }

  /** Adds {@code item} to the basket. */
  public void add(String item) {
    items.add(item.toLowerCase(Locale.ROOT));
  }

  /** Returns the number of items currently in the basket. */
  public int itemCount() {
    return items.size();
  }

  /** Returns the total price in pence after applying the current scenario's {@link Discount}. */
  public int total() {
    int subtotal = items.stream().mapToInt(priceList::priceOf).sum();
    return discount.apply(subtotal);
  }
}
