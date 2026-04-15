package dev.joss.dagger.cucumber.it;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-scenario shopping basket. A fresh instance is provided for every scenario via {@link
 * ScenarioModule#provideBasket}.
 *
 * <p>Receives the singleton {@link PriceList} and the per-scenario {@link Discount}.
 */
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
