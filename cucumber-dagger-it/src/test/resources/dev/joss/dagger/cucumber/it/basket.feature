Feature: Basket

  # Verifies that the singleton PriceList (from PriceListModule) is consulted correctly.
  Scenario: Single item price comes from the shared price list
    Then "apple" costs 30p in the price list
    When I add "apple" to my basket
    Then my basket total is 30p

  # Verifies that multiple items accumulate correctly.
  Scenario: Adding multiple items sums their prices
    When I add "apple" to my basket
    And I add "banana" to my basket
    And I add "cherry" to my basket
    Then my basket contains 3 items
    And my basket total is 95p

  # Verifies that the scoped Discount affects the total.
  Scenario: A percentage discount reduces the basket total
    Given a 20% discount is applied
    When I add "cherry" to my basket
    Then my basket total is 40p
