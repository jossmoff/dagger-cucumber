# Configuration reference

This page describes every configurable point in cucumber-dagger: the two annotations, root provision methods, module registration, and step definition discovery rules.

## @CucumberDaggerConfiguration

### Placement

Apply `@CucumberDaggerConfiguration` to a `@Component` interface in your test sources. This interface is the root of your Dagger graph for the test run.

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {}
```

### Rules

- **Interface only.** The annotation must be placed on an interface. Placing it on a class causes a compile error:
  `@CucumberDaggerConfiguration can only be applied to interfaces`
- **Exactly one per project.** Only one interface in the test classpath may carry this annotation. A second annotated interface causes a compile error:
  `Only one @CucumberDaggerConfiguration is allowed`

### Interaction with @Singleton

Annotate the root component interface with `@Singleton` so that Dagger applies the singleton scope to root bindings. The processor propagates any scope annotations from your interface to the generated wrapper component, so you do not need to duplicate them.

## @ScenarioScope

### Placement

Apply `@ScenarioScope` to `@Provides` methods inside `@Module` classes. List those modules in the `modules` attribute of your root `@Component`.

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

### Rules

- **Method-level only on `@Provides` methods.** Although the annotation's `@Target` includes `TYPE`, class-level usage is not supported by the processor. Annotating a class with `@ScenarioScope` does not make its constructor-injected instances scenario-scoped.
- **No qualifier annotations.** You cannot combine `@ScenarioScope` with `@Named` or any other qualifier annotation on the same `@Provides` method. Doing so causes a compile error:
  `Qualified @ScenarioScope provider methods are not currently supported`

### Lifecycle

A new instance is created immediately before each scenario starts and is discarded when the scenario ends. Dagger caches the instance within the scenario so that multiple injection sites receive the same object.

## Root provision methods

You can declare zero-argument abstract methods on your root component interface to expose root-scoped objects to the `GeneratedComponentResolver.resolveRoot` dispatch. This is useful when a step definition class needs to be injected from the root component rather than the scenario subcomponent.

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {

    // Exposes PriceList for resolveRoot dispatch
    PriceList priceList();
}
```

The generated `GeneratedCucumberIntegrationTestConfig` extends `IntegrationTestConfig`, so Dagger generates an implementation of `priceList()`. The generated `GeneratedComponentResolver` adds a dispatch branch for `PriceList` in its `resolveRoot` method.

Note that step definition classes with an `@Inject` constructor are always discovered automatically and do not need a manual provision method.

## Module registration

List all modules - both root-scoped and scenario-scoped - in the `modules` attribute of `@Component` on your root interface. You do not need to separate them yourself.

```java
@Component(modules = {PriceListModule.class, ScenarioModule.class})
```

The processor inspects each listed module for `@Provides @ScenarioScope` methods. Modules that contain such methods are treated as _scoped modules_: the processor includes them in `GeneratedScopedModule` (the subcomponent's module) and excludes them from the generated wrapper root component to prevent Dagger's "module repeated in subcomponent" error.

Modules that contain only `@Singleton` or unscoped bindings remain on the root component unchanged.

## Step definition discovery rules

The processor treats a class as a step definition if it meets all of the following criteria:

1. It has a constructor annotated with `@Inject` (from `jakarta.inject`).
2. Its package name is equal to, or starts with, the package of the root component interface.

No additional annotation is needed. Any class satisfying these two rules in the glue package tree receives a provision method on `GeneratedScopedComponent` and a dispatch branch in `GeneratedComponentResolver`.

For example, if your root component is in `com.example.tests`, then classes in `com.example.tests`, `com.example.tests.checkout`, and `com.example.tests.basket` are all discovered. Classes in `com.example.other` are not.

## Supported Dagger features

| Feature            | Applicable scope                  | Example                                                          |
|--------------------|-----------------------------------|------------------------------------------------------------------|
| `@Provides`        | Root or scenario                  | `@Provides @Singleton static PriceList provide(...)`             |
| `@Binds`           | Root or scenario                  | `@Binds @Singleton abstract Formatter bind(PlainFormatter impl)` |
| `@BindsOptionalOf` | Root                              | `@BindsOptionalOf abstract VoucherService optional()`            |
| `@IntoSet`         | Root                              | `@Provides @IntoSet @Singleton static String provideCard()`      |
| `Provider<T>`      | Root or scenario (injection site) | `Provider<ReceiptFormatter> formatter` in constructor            |
| `@Singleton`       | Root                              | `@Provides @Singleton static PriceList provide(...)`             |
| `@ScenarioScope`   | Scenario                          | `@Provides @ScenarioScope static Basket provide(...)`            |

Qualified `@ScenarioScope` bindings (combining `@ScenarioScope` with `@Named` or a custom qualifier) are not currently supported.
