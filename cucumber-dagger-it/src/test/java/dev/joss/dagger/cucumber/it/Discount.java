package dev.joss.dagger.cucumber.it;

import jakarta.inject.Inject;

/**
 * Per-scenario discount applied at checkout. A fresh instance starting at 0% is provided for each
 * scenario via {@link ScenarioModule#provideDiscount}; step definitions can call {@link
 * #setPercent(int)} to configure it within a scenario.
 */
public final class Discount {

  private int percent;

  @Inject
  public Discount() {
    this.percent = 0;
  }

  /** Sets the discount percentage for this scenario. */
  public void setPercent(int percent) {
    this.percent = percent;
  }

  /** Returns the current discount percentage. */
  public int getPercent() {
    return percent;
  }

  /** Returns {@code price} reduced by the current discount percentage (rounded down). */
  public int apply(int price) {
    return price - (price * percent / 100);
  }
}
