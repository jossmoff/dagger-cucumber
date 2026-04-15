# dagger-cucumber

A Cucumber extension that uses [Dagger 2](https://dagger.dev) for compile-time dependency injection in your test suite, replacing runtime reflection with generated code.

## Quick start

### 1. Add dependencies

```kotlin
dependencies {
    testImplementation(platform("dev.joss:cucumber-dagger-bom:<version>"))
    testImplementation("dev.joss:cucumber-dagger")
    testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
    testAnnotationProcessor("com.google.dagger:dagger-compiler:<dagger-version>")
}
```

### 2. Declare a root component

Annotate exactly one Dagger `@Component` interface in your test sources with `@CucumberDaggerConfiguration`. You only need to list your own modules - the processor adds the generated `CucumberDaggerModule` automatically.

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {AppModule.class})
public interface IntegrationTestConfig {}
```

### 3. Provide scenario-scoped bindings

Use `@ScenarioScope` on `@Provides` methods inside a `@Module` to bind objects whose lifetime matches a single scenario. Dagger creates a fresh instance at the start of each scenario and discards it at the end.

```java
@Module
public final class AppModule {

    @Provides
    @ScenarioScope
    static Basket provideBasket(PriceList priceList) {
        return new Basket(priceList);
    }
}
```

### 4. Inject into step definitions

Annotate the constructor of any step definition class with `@Inject`. Dagger resolves and injects all parameters automatically.

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

## Documentation

- [Getting started](docs/getting-started.md)
- [Architecture](docs/architecture.md)
- [Configuration reference](docs/configuration-reference.md)
- [Scenario scope](docs/scenario-scope.md)
- [Providing static values](docs/providing-static-values.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Migrating](docs/migrating.md)

## Modules

| Module | Description |
|---|---|
| `cucumber-dagger` | Runtime library: Cucumber `ObjectFactory`/`Backend` SPI implementation, API annotations (`@CucumberDaggerConfiguration`, `@ScenarioScope`), and internal lifecycle management. |
| `cucumber-dagger-processor` | Annotation processor: generates the Dagger subcomponent, modules, wrapper component, and `ComponentResolver` at compile time. |
| `cucumber-dagger-bom` | Bill of Materials: import this platform dependency to align all `dev.joss:cucumber-dagger*` artifact versions without specifying them individually. |

## Licence

This project is released under the [MIT Licence](LICENSE).
