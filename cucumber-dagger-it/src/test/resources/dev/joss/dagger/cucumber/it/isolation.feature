Feature: Scenario isolation

  # The @CucumberScoped Basket starts empty in every scenario even though the
  # singleton PriceList is shared across the whole test run.
  Scenario: Basket is empty at the start of each scenario
    Then my basket contains 0 items
    And my basket total is 0p

  # Items added in the previous scenario must not be visible here.
  Scenario: Items added in one scenario do not bleed into the next
    When I add "apple" to my basket
    Then my basket contains 1 item

  # The scoped Discount is also fresh (0%) even if a previous scenario set a discount.
  Scenario: Discount resets between scenarios
    Given a 20% discount is applied
    When I add "cherry" to my basket
    Then my basket total is 40p
