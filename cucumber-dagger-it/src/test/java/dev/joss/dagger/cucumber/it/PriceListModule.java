package dev.joss.dagger.cucumber.it;

import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * Root module demonstrating all common Dagger binding styles.
 *
 * <ul>
 *   <li>{@code @Provides} - explicit factory method; use when construction logic is needed.
 *   <li>{@code @Binds} - zero-overhead interface-to-impl binding; requires an {@code @Inject}
 *       constructor on the implementation.
 *   <li>{@code @BindsOptionalOf} - declares that a type <em>may</em> be bound; injection sites
 *       receive {@code Optional<T>}, which is empty when no binding is present.
 *   <li>{@code @IntoSet} - contributes one element to a {@code Set<T>} multibinding; any number of
 *       modules can contribute to the same set.
 *   <li>{@code @BindsInstance + @Provides} - receives the raw {@code @Nullable} value injected via
 *       the component builder and resolves it with a system-property fallback before exposing it as
 *       a guaranteed non-null binding (see {@link #provideStoreName}).
 * </ul>
 */
@Module
public abstract class PriceListModule {

  // @Provides - explicit construction; use when you need to pass arguments or call a factory.
  @Provides
  @Singleton
  static PriceList providePriceList() {
    return new PriceList(Map.of("apple", 30, "banana", 15, "cherry", 50));
  }

  // @BindsInstance + @Provides: the raw @Nullable value injected via the component builder is
  // resolved here with a system-property fallback before being used elsewhere in the graph.
  @Provides
  @Singleton
  @Named("storeName")
  static String provideStoreName(@Named("rawStoreName") @Nullable String rawStoreName) {
    return rawStoreName != null ? rawStoreName : System.getProperty("store.name", "Test Store");
  }

  // @Binds - tells Dagger "when ReceiptFormatter is needed, use PlainReceiptFormatter".
  // No body required; Dagger generates the delegation at compile time.
  @Binds
  @Singleton
  abstract ReceiptFormatter bindReceiptFormatter(PlainReceiptFormatter impl);

  // @BindsOptionalOf - marks VoucherService as optionally available.
  // Because no @Provides/@Binds for VoucherService exists, Optional<VoucherService> is empty.
  @BindsOptionalOf
  abstract VoucherService optionalVoucherService();

  // @IntoSet - each method contributes one element to Set<String>.
  // All @IntoSet bindings for the same type across all modules are collected into one Set.
  @Provides
  @IntoSet
  @Singleton
  static String provideCardPayment() {
    return "CARD";
  }

  @Provides
  @IntoSet
  @Singleton
  static String provideCashPayment() {
    return "CASH";
  }
}
