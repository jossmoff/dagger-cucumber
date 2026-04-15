# cucumber-dagger-it

Integration test module for dagger-cucumber.

## Not a published artefact

This module is not published to Maven Central. It exists solely to verify that the `cucumber-dagger` runtime and `cucumber-dagger-processor` annotation processor work correctly together end-to-end under a real Gradle build.

## What it demonstrates

The integration tests exercise all common Dagger binding styles and injection patterns:

| Feature | Where it appears |
|---|---|
| `@Provides` | `PriceListModule` - explicit factory method for `PriceList` and payment method multibindings |
| `@Binds` | `PriceListModule` - zero-overhead interface-to-implementation binding for `ReceiptFormatter` |
| `@BindsOptionalOf` | `PriceListModule` - declares `VoucherService` as optionally available; injection sites receive `Optional.empty()` when no binding is present |
| `@IntoSet` | `PriceListModule` - contributes `"CARD"` and `"CASH"` to a `Set<String>` multibinding |
| `Provider<T>` | `CheckoutSteps` - `Provider<ReceiptFormatter>` is injected and called on demand |
| `@ScenarioScope` | `ScenarioModule` - `Basket` and `Discount` are recreated fresh for every scenario |

## Running the tests

```bash
./gradlew :cucumber-dagger-it:test
```

## Further reading

- [Getting started](../docs/getting-started.md) - how to set up dagger-cucumber in your own project
