# Scenario scope

Scenario scope is the mechanism cucumber-dagger uses to provide fresh object instances for each Cucumber scenario. This page explains what it means, how to declare it, and how to use it to share state between step definition classes.

## What scenario scope means

When you annotate a `@Provides` method with `@ScenarioScope`, Dagger creates one instance of the returned type at the start of each scenario and caches it for the duration of that scenario. When the scenario ends the subcomponent is discarded, taking all scenario-scoped instances with it. The next scenario receives entirely new instances.

This is different from `@Singleton`, which creates one instance for the entire test run and reuses it across all scenarios.

## Declaring a scenario-scoped binding

Add a `@Provides @ScenarioScope` method to a `@Module` class and list that module in the `modules` attribute of your root `@Component`.

```java
@Module
public final class ScenarioModule {

    @Provides
    @ScenarioScope
    static Basket provideBasket(PriceList priceList) {
        return new Basket(priceList);
    }
}
```

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {}
```

The processor detects `@ScenarioScope` on `provideBasket` and moves `ScenarioModule` to the generated subcomponent. You do not need to split your module list manually.

## Injecting scenario-scoped objects

Declare the type as a constructor parameter in a step definition class or in another scoped binding. Dagger resolves it from the subcomponent.

```java
public final class CheckoutSteps {

    private final Basket basket;

    @Inject
    public CheckoutSteps(Basket basket) {
        this.basket = basket;
    }

    @When("I add {string} to my basket")
    public void iAddToMyBasket(String item) {
        basket.add(item);
    }
}
```

## Sharing state between step definitions

Two step definition classes that both inject the same `@ScenarioScope` type receive the same instance within a single scenario. This is how you share mutable state between step definitions without static fields.

```java
// Both classes inject Basket
public final class AddItemSteps {

    private final Basket basket;

    @Inject
    public AddItemSteps(Basket basket) {
        this.basket = basket;
    }

    @When("I add {string} to my basket")
    public void iAddToMyBasket(String item) {
        basket.add(item);
    }
}

public final class TotalSteps {

    private final Basket basket;

    @Inject
    public TotalSteps(Basket basket) {
        this.basket = basket;
    }

    @Then("my basket total is {int}p")
    public void myBasketTotalIs(int total) {
        assertThat(basket.total()).isEqualTo(total);
    }
}
```

`AddItemSteps` and `TotalSteps` receive the same `Basket` instance because Dagger caches it within the `GeneratedScopedComponent` for the duration of the scenario.

## Dependencies across scopes

A `@ScenarioScope` binding can depend on a `@Singleton` binding from the root component. Dagger resolves the singleton dependency from the root component and passes it to the scoped factory method.

```java
@Module
public final class ScenarioModule {

    // Basket is scenario-scoped but depends on the singleton PriceList
    @Provides
    @ScenarioScope
    static Basket provideBasket(PriceList priceList, Discount discount) {
        return new Basket(priceList, discount);
    }

    @Provides
    @ScenarioScope
    static Discount provideDiscount() {
        return new Discount();
    }
}
```

`PriceList` is provided by a root module with `@Singleton`. Each scenario gets a fresh `Basket` and `Discount`, but they all share the same `PriceList` instance.

The reverse - a `@Singleton` binding depending on a `@ScenarioScope` binding - is not valid and Dagger will report it as a scope violation at compile time.

## What cannot be scenario-scoped

### Qualified providers

You cannot combine `@ScenarioScope` with a qualifier annotation such as `@Named` on the same `@Provides` method. The processor does not currently support qualified scoped bindings and will report a compile error if you attempt this.

### Class-level annotations

Although the `@ScenarioScope` annotation's `@Target` includes `TYPE`, annotating a class with `@ScenarioScope` does not make its constructor-injected instances scenario-scoped. Scope management in cucumber-dagger works entirely through `@Provides` methods on modules, not through class-level scope annotations.

## Worked example: shopping basket

The integration tests in this repository use a shopping basket domain that demonstrates all aspects of scenario scope in one place.

```java
// PriceListModule.java - root module, PriceList is shared across all scenarios
@Module
public abstract class PriceListModule {

    @Provides
    @Singleton
    static PriceList providePriceList() {
        return new PriceList(Map.of("apple", 30, "banana", 15, "cherry", 50));
    }
}
```

```java
// ScenarioModule.java - scenario module, Discount and Basket are fresh each scenario
@Module
public final class ScenarioModule {

    @Provides
    @ScenarioScope
    static Discount provideDiscount() {
        return new Discount();
    }

    @Provides
    @ScenarioScope
    static Basket provideBasket(PriceList priceList, Discount discount) {
        return new Basket(priceList, discount);
    }
}
```

```java
// IntegrationTestConfig.java - root component listing both modules
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {}
```

```java
// CheckoutSteps.java - step definitions receive both singleton and scenario-scoped objects
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

    @Given("a {int}% discount is applied")
    public void aDiscountIsApplied(int percent) {
        discount.setPercent(percent);
    }

    @When("I add {string} to my basket")
    public void iAddToMyBasket(String item) {
        basket.add(item);
    }

    @Then("my basket total is {int}p")
    public void myBasketTotalIs(int total) {
        assertThat(basket.total()).isEqualTo(total);
    }
}
```

Each scenario starts with an empty `Basket` and a zero-percent `Discount`. Mutations made in one scenario do not affect the next because the scoped subcomponent - and all objects it created - is discarded after `disposeWorld()`.
