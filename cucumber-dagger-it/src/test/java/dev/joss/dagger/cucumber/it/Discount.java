package dev.joss.dagger.cucumber.it;

import dev.joss.dagger.cucumber.api.CucumberScoped;
import javax.inject.Inject;

/**
 * Per-scenario discount applied at checkout (Style-A {@code @CucumberScoped}). A fresh instance
 * starting at 0% is created for each scenario; step definitions can call {@link #setPercent(int)}
 * to configure it within a scenario.
 */
@CucumberScoped
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
