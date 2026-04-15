# Troubleshooting

This page lists the errors you are most likely to encounter when configuring cucumber-dagger, along with their causes and fixes. Each section shows the exact message that appears, explains what triggered it, and describes the steps to resolve it.

## "Type X is not provided by any component"

```
java.lang.IllegalStateException: Type com.example.tests.CheckoutSteps is not provided by any component.
 Ensure it is a step-definition class with an @Inject constructor, or is bound via @Provides @ScenarioScope
 in a module listed on your @CucumberDaggerConfiguration component.
```

**Cause.** `DaggerObjectFactory.getInstance` was called for a type that the generated resolver does not know about. This happens when:

- The class has no `@Inject`-annotated constructor, so the processor did not generate a provision method for it.
- The class is in a package that is outside the glue package tree (that is, its package does not start with the root component's package).
- The type is a non-step-definition object that was never bound via `@Provides` in any module.

**Fix.**

1. Add `@Inject` to the class's constructor.
2. Verify that the class is in the same package as the root component or in a sub-package of it.
3. If the class is not a step definition but is needed as a dependency, add a `@Provides` method for it in a module that is listed on the root component.

## "GeneratedComponentResolver not found"

```
java.lang.IllegalStateException: GeneratedComponentResolver not found.
 Ensure the cucumber-dagger-processor annotationProcessor dependency is configured
 and @CucumberDaggerConfiguration is present on your root component.
```

**Cause.** The runtime tried to load `dev.joss.dagger.cucumber.generated.GeneratedComponentResolver` by name but the class does not exist on the classpath. This class is written by the annotation processor.

**Fix.**

1. Confirm that `cucumber-dagger-processor` is declared as an `annotationProcessor` dependency (not `implementation` or `testImplementation`):
   ```kotlin
   testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
   ```
2. Confirm that exactly one interface in your test sources is annotated with `@CucumberDaggerConfiguration`.
3. Run a clean build (`./gradlew clean test`) to force the processor to run again.

## "Only one @CucumberDaggerConfiguration is allowed"

```
error: Only one @CucumberDaggerConfiguration is allowed
```

**Cause.** Two or more interfaces in your test sources are annotated with `@CucumberDaggerConfiguration`. The processor requires exactly one.

**Fix.** Remove `@CucumberDaggerConfiguration` from all but one interface. If you have separate component interfaces for different test suites, consolidate them into one root component or run them in separate Gradle subprojects.

## "@CucumberDaggerConfiguration can only be applied to interfaces"

```
error: @CucumberDaggerConfiguration can only be applied to interfaces
```

**Cause.** The annotation was placed on a class rather than an interface.

**Fix.** Convert the annotated type to an interface, or move `@CucumberDaggerConfiguration` to an existing interface. For example:

```java
// Incorrect - placed on a class
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {AppModule.class})
public class IntegrationTestConfig {}   // <-- class, not interface

// Correct - placed on an interface
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {AppModule.class})
public interface IntegrationTestConfig {}
```

## "Qualified @ScenarioScope provider methods are not currently supported"

```
error: Qualified @ScenarioScope provider methods are not currently supported
```

**Cause.** A `@Provides` method in a module is annotated with both `@ScenarioScope` and a qualifier annotation such as `@Named`.

```java
// This is not supported
@Provides
@ScenarioScope
@Named("primary")
static Basket provideBasket(PriceList priceList) { ... }
```

**Fix.** Remove the qualifier from the `@ScenarioScope` method. If you need to distinguish multiple bindings of the same type, introduce a dedicated wrapper type instead of using a qualifier:

```java
public final class PrimaryBasket {
    public final Basket basket;
    public PrimaryBasket(Basket basket) { this.basket = basket; }
}

@Provides
@ScenarioScope
static PrimaryBasket providePrimaryBasket(PriceList priceList) {
    return new PrimaryBasket(new Basket(priceList));
}
```

## "does not have a static create() method"

```
java.lang.IllegalStateException: com.example.tests.DaggerGeneratedCucumberIntegrationTestConfig
 does not have a static create() method. Dagger only generates create() when all modules have
 no-arg or static @Provides methods and no @BindsInstance parameters are required.
 If your component requires a builder, this is not yet supported -
 see https://github.com/jossmoff/dagger-cucumber/issues for tracking.
```

**Cause.** Dagger did not generate a static `create()` method on the root component because the component requires a builder - for example, because a module has a non-static constructor, or because `@BindsInstance` was used on the component builder.

**Fix.** Ensure that every module listed on the root component either:

- Has no constructor parameters (Dagger can instantiate it automatically), or
- Provides all of its bindings via `static @Provides` methods (Dagger does not need to instantiate the module at all).

`@BindsInstance` on the root component builder is not yet supported by cucumber-dagger. Use a `@Provides @Singleton` method in a root module to supply values that would otherwise be passed via `@BindsInstance`. See [providing-static-values.md](providing-static-values.md) for examples.

## "Multiple CucumberDaggerComponent factories found"

```
java.lang.IllegalStateException: Multiple CucumberDaggerComponent factories found:
 [com.example.tests.DaggerGeneratedCucumberIntegrationTestConfig,
  com.other.tests.DaggerGeneratedCucumberOtherComponent]
```

**Cause.** The `META-INF/services/dev.joss.dagger.cucumber.api.CucumberDaggerComponent` service file contains more than one entry. Under normal circumstances this cannot happen in a single Gradle subproject, but it can occur when:

- Two separate subprojects that each ran the annotation processor have their class outputs merged onto a single classpath.
- A JAR containing a previously generated service file was included as a dependency.

**Fix.** Ensure that only one Gradle subproject runs the cucumber-dagger annotation processor and that the generated class output from other subprojects is not on the test classpath. Each independent test suite should have its own Gradle subproject.

## Enable Dagger compiler error messages

When Dagger cannot satisfy a dependency in the graph it prints a detailed error during compilation. These messages identify the exact type that is missing, the full injection chain, and the component involved.

If your build fails at runtime rather than compile time, check whether Dagger's own compiler is configured. Both processors must be present:

```kotlin
testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
testAnnotationProcessor("com.google.dagger:dagger-compiler:<version>")
```

If the `dagger-compiler` processor is absent, Dagger's generated `Dagger*` classes will not exist and you will see `ClassNotFoundException` or `NoSuchMethodException` at runtime rather than a clear compile-time message. Adding `dagger-compiler` turns most runtime failures into compile-time failures with actionable descriptions.

To see all annotation processor output during a build, run Gradle with `--info`:

```
./gradlew test --info 2>&1 | grep -i "dagger\|cucumber\|error"
```
