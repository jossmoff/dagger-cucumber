package dev.joss.dagger.cucumber.it;

/**
 * Applies a voucher to a basket total. No binding is provided in the test suite, so injection
 * points that declare {@code Optional<VoucherService>} receive {@code Optional.empty()} - see
 * {@link PriceListModule#optionalVoucherService()}.
 */
public interface VoucherService {

  /** Returns the post-voucher price for {@code totalPence}. */
  int apply(int totalPence);
}
