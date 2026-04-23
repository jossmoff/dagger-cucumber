package dev.joss.dagger.cucumber.it;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Plain-text implementation of {@link ReceiptFormatter}. Bound as a {@code @Singleton} via
 * {@code @Binds} in {@link PriceListModule} - no {@code @Provides} method is needed because Dagger
 * can construct it directly from the {@code @Inject} constructor.
 *
 * <p>{@code storeName} is a {@code @Named} singleton provided by {@link PriceListModule} from the
 * {@code @BindsInstance} value on the component builder, falling back to the {@code store.name}
 * system property.
 */
public final class PlainReceiptFormatter implements ReceiptFormatter {

  private final String storeName;

  @Inject
  public PlainReceiptFormatter(@Named("storeName") String storeName) {
    this.storeName = storeName;
  }

  @Override
  public String format(int itemCount, int totalPence) {
    return "[" + storeName + "] " + itemCount + " item(s)  total: " + totalPence + "p";
  }
}
