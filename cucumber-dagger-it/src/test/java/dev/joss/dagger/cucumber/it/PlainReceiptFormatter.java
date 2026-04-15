package dev.joss.dagger.cucumber.it;

import jakarta.inject.Inject;

/**
 * Plain-text implementation of {@link ReceiptFormatter}. Bound as a {@code @Singleton} via
 * {@code @Binds} in {@link PriceListModule} - no {@code @Provides} method is needed because Dagger
 * can construct it directly from the {@code @Inject} constructor.
 */
public final class PlainReceiptFormatter implements ReceiptFormatter {

  @Inject
  public PlainReceiptFormatter() {}

  @Override
  public String format(int itemCount, int totalPence) {
    return itemCount + " item(s)  total: " + totalPence + "p";
  }
}
