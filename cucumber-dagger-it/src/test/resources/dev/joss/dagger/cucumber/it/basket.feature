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

  # Provider<T> - the ReceiptFormatter is fetched from its Provider on demand after checkout.
  Scenario: A receipt can be generated after items are added
    When I add "apple" to my basket
    And I add "banana" to my basket
    Then a receipt is generated

  # @IntoSet - Set<String> is populated from all @Provides @IntoSet methods across all modules.
  Scenario: Standard payment methods are accepted
    Then "CARD" is an accepted payment method
    And "CASH" is an accepted payment method

  # @BindsOptionalOf - Optional<VoucherService> is empty because no binding is provided.
  Scenario: No voucher service is configured by default
    Then no voucher service is configured
