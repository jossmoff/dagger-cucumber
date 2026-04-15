# Getting started

This guide walks you through adding cucumber-dagger to a new or existing Gradle project and running your first Cucumber scenario with Dagger injection.

## Prerequisites

- Java 21 or later
- Gradle (Kotlin DSL)
- Familiarity with the basics of Dagger 2: components, modules, and `@Provides` methods

## 1. Add dependencies

cucumber-dagger publishes a Bill of Materials (BOM) that aligns the versions of `cucumber-dagger` and `cucumber-dagger-processor`. Import the BOM, then declare the runtime dependency and the two annotation processors.

```kotlin
// build.gradle.kts
dependencies {
    // BOM - aligns cucumber-dagger and cucumber-dagger-processor versions
    testImplementation(platform("dev.joss:cucumber-dagger-bom:<version>"))

    // Runtime: Cucumber Backend and ObjectFactory SPIs
    testImplementation("dev.joss:cucumber-dagger")

    // Annotation processors: cucumber-dagger-processor generates the Dagger wiring;
    // dagger-compiler generates the Dagger implementations
    testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
    testAnnotationProcessor("com.google.dagger:dagger-compiler:<dagger-version>")

    // Cucumber and JUnit Platform
    testImplementation(platform("org.junit:junit-bom:<junit-version>"))
    testImplementation("io.cucumber:cucumber-java:<cucumber-version>")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:<cucumber-version>")
    testImplementation("org.junit.platform:junit-platform-suite-api")
    testRuntimeOnly("org.junit.platform:junit-platform-suite-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-platform-suite")
    }
}
```

If you use a [version catalogue](https://docs.gradle.org/current/userguide/platforms.html), add entries for `cucumber-dagger-bom`, `cucumber-dagger`, and `cucumber-dagger-processor` under `[libraries]` and reference them via `libs.*`.

## 2. Create the root component

The root component is a standard Dagger `@Component` interface that you additionally annotate with `@CucumberDaggerConfiguration`. Exactly one such interface must exist in your test sources. Annotate it with `@Singleton` so that Dagger applies the singleton scope to the root graph.

```java
package com.example.tests;

import dagger.Component;
import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;
import jakarta.inject.Singleton;

@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {}
```

The annotation processor reads this interface and generates all of the Dagger wiring that the runtime needs. You do not need to extend any framework interface yourself.

See [configuration-reference.md](configuration-reference.md) for the full set of rules that apply to the root component.

## 3. Add a root module

Root modules supply bindings that live for the entire test run - typically services, repositories, or read-only configuration that all scenarios share.

```java
package com.example.tests;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.util.Map;

@Module
public abstract class PriceListModule {

    @Provides
    @Singleton
    static PriceList providePriceList() {
        return new PriceList(Map.of("apple", 30, "banana", 15, "cherry", 50));
    }
}
```

List `PriceListModule.class` in the `modules` attribute of `@Component` on your root component.

## 4. Add scenario-scoped bindings

Bindings that should be created fresh for each scenario go in a separate module. Annotate each `@Provides` method with `@ScenarioScope`.

```java
package com.example.tests;

import dagger.Module;
import dagger.Provides;
import dev.joss.dagger.cucumber.api.ScenarioScope;

@Module
public final class ScenarioModule {

    @Provides
    @ScenarioScope
    static Basket provideBasket(PriceList priceList) {
        return new Basket(priceList);
    }
}
```

List `ScenarioModule.class` in the `modules` attribute alongside your root modules. The processor moves scoped modules to the generated subcomponent automatically - you do not need to do anything special.

See [scenario-scope.md](scenario-scope.md) for a detailed explanation of the scenario scope lifecycle.

## 5. Write a step definition

A step definition class needs an `@Inject`-annotated constructor and must live in the same package as the root component, or in a sub-package of it. No additional annotation is required.

```java
package com.example.tests;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Then("my basket contains {int} item(s)")
    public void myBasketContains(int count) {
        assertThat(basket.itemCount()).isEqualTo(count);
    }
}
```

The processor discovers this class at compile time because it has an `@Inject` constructor and sits in the `com.example.tests` package tree. It generates a provision method for it on the scenario-scoped subcomponent.

## 6. Configure JUnit Platform

Create `src/test/resources/junit-platform.properties` and point Cucumber at the glue package - the package that contains your root component and step definitions.

```properties
cucumber.glue=com.example.tests
cucumber.publish.quiet=true
```

## 7. Create the suite runner

Create a JUnit Platform suite class to act as the entry point for Gradle's test task.

```java
package com.example.tests;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.example.tests")
public class CucumberRunnerTest {}
```

## 8. Run the tests

```
./gradlew test
```

Gradle compiles your sources, the annotation processors run and generate the Dagger wiring, Dagger's own compiler generates the implementations, and then the test task executes your scenarios.

## Next steps

- [configuration-reference.md](configuration-reference.md) - full documentation for every annotation and rule
- [scenario-scope.md](scenario-scope.md) - sharing state between step definition classes within a scenario
- [architecture.md](architecture.md) - how the processor and runtime fit together
