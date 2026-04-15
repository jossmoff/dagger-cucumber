package dev.joss.dagger.cucumber.it;

/** Formats a checkout receipt. Bound via {@code @Binds} in {@link PriceListModule}. */
public interface ReceiptFormatter {

  /**
   * Returns a human-readable receipt for {@code itemCount} items with a total of {@code totalPence}
   * pence.
   */
  String format(int itemCount, int totalPence);
}
