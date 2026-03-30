package dev.joss.dagger.cucumber.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.inject.Inject;

/**
 * Step definitions for checkout scenarios. Injects both the per-scenario {@link Basket} and {@link
 * Discount}, as well as the singleton {@link PriceList} to demonstrate that root-component bindings
 * are available as step-def constructor arguments.
 */
public final class CheckoutSteps {

  private final Basket basket;
  private final Discount discount;
  private final PriceList priceList;

  @Inject
  public CheckoutSteps(Basket basket, Discount discount, PriceList priceList) {
    this.basket = basket;
    this.discount = discount;
    this.priceList = priceList;
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
}
