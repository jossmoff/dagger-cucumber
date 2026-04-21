package dev.joss.dagger.cucumber.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import java.util.Optional;
import java.util.Set;

/**
 * Step definitions for checkout scenarios.
 *
 * <p>Constructor parameters demonstrate several Dagger injection styles:
 *
 * <ul>
 *   <li>{@code Basket}, {@code Discount} - per-scenario bindings from {@link ScenarioModule}.
 *   <li>{@code PriceList} - singleton from {@link PriceListModule}; shows root bindings are
 *       available inside the scenario scope.
 *   <li>{@code Provider<ReceiptFormatter>} - fetched on demand; Dagger injects the {@code Provider}
 *       wrapper rather than the instance directly.
 *   <li>{@code Optional<VoucherService>} - absent because no binding is provided; populated
 *       automatically when a {@code VoucherService} binding is added to the graph.
 *   <li>{@code Set<String> acceptedPaymentMethods} - collected from all {@code @IntoSet} bindings
 *       for {@code String} declared across all modules.
 *   <li>{@code ReceiptFormatter} (via {@code Provider}) - uses the {@code @Named("storeName")}
 *       singleton resolved from the {@code @BindsInstance} value on the component builder.
 * </ul>
 */
public final class CheckoutSteps {

  private final Basket basket;
  private final Discount discount;
  private final PriceList priceList;
  private final Provider<ReceiptFormatter> receiptFormatter;
  private final Optional<VoucherService> voucherService;
  private final Set<String> acceptedPaymentMethods;

  @Inject
  public CheckoutSteps(
      Basket basket,
      Discount discount,
      PriceList priceList,
      Provider<ReceiptFormatter> receiptFormatter,
      Optional<VoucherService> voucherService,
      Set<String> acceptedPaymentMethods) {
    this.basket = basket;
    this.discount = discount;
    this.priceList = priceList;
    this.receiptFormatter = receiptFormatter;
    this.voucherService = voucherService;
    this.acceptedPaymentMethods = acceptedPaymentMethods;
  }

  @When("I add {string} to my basket")
  public void iAddToMyBasket(String item) {
    basket.add(item);
  }

  @Given("a {int}% discount is applied")
  public void aDiscountIsApplied(int percent) {
    discount.setPercent(percent);
  }

  @Then("my basket contains {int} item(s)")
  public void myBasketContains(int count) {
    assertThat(basket.itemCount()).isEqualTo(count);
  }

  @Then("my basket total is {int}p")
  public void myBasketTotalIs(int total) {
    assertThat(basket.total()).isEqualTo(total);
  }

  @Then("{string} costs {int}p in the price list")
  public void itemCostsInPriceList(String item, int price) {
    assertThat(priceList.priceOf(item)).isEqualTo(price);
  }

  // Provider<T> - the formatter is retrieved from its Provider on demand.
  @Then("a receipt is generated")
  public void aReceiptIsGenerated() {
    String receipt = receiptFormatter.get().format(basket.itemCount(), basket.total());
    assertThat(receipt).isNotEmpty();
  }

  // @BindsInstance - storeName is injected via the component builder and threaded through to the
  // formatter. The default value comes from System.getProperty("store.name", "Test Store").
  @Then("the receipt header shows {string}")
  public void theReceiptHeaderShows(String expectedStoreName) {
    String receipt = receiptFormatter.get().format(basket.itemCount(), basket.total());
    assertThat(receipt).startsWith("[" + expectedStoreName + "]");
  }

  // Set<T> multibinding - the set is populated at compile time from all @IntoSet methods.
  @Then("{string} is an accepted payment method")
  public void isAnAcceptedPaymentMethod(String method) {
    assertThat(acceptedPaymentMethods).contains(method);
  }

  // Optional<T> - resolves to Optional.empty() when no VoucherService binding is present.
  @Then("no voucher service is configured")
  public void noVoucherServiceIsConfigured() {
    assertThat(voucherService).isEmpty();
  }
}
