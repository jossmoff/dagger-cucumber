# Getting started

## Prerequisites

- Java 21+
- Gradle (Kotlin DSL)
- Basic familiarity with Dagger 2: components, modules, `@Provides`

## 1. Add dependencies

```kotlin
// build.gradle.kts
dependencies {
    testImplementation(platform("dev.joss:cucumber-dagger-bom:<version>"))
    testImplementation("dev.joss:cucumber-dagger")

    testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
    testAnnotationProcessor("com.google.dagger:dagger-compiler:<dagger-version>")

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

## 2. Create the root component

Annotate a `@Component` interface with `@CucumberDaggerConfiguration`. Add a `@Component.Builder` with `@Nullable @BindsInstance` setters for any values you want to inject from system properties or environment variables.

```java
package com.example.tests;

import dagger.BindsInstance;
import dagger.Component;
import dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

@CucumberDaggerConfiguration
@Singleton
@Component(modules = {AppModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {

    @Component.Builder
    interface Builder {
        @BindsInstance Builder baseUrl(@Nullable String baseUrl);
        @BindsInstance Builder apiKey(@Nullable String apiKey);
        IntegrationTestConfig build();
    }
}
```

> All `@BindsInstance` parameters must be `@Nullable` — the runtime calls `builder().build()` without setting any values.

## 3. Add a root module

Root modules supply bindings shared for the entire test run. Read env-var-sourced values from the `@Nullable @BindsInstance` parameters injected by the builder.

```java
package com.example.tests;

import dagger.Module;
import dagger.Provides;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

@Module
public abstract class AppModule {

    @Provides
    @Singleton
    static ApiClient provideApiClient(@Nullable String baseUrl, @Nullable String apiKey) {
        String url = baseUrl != null ? baseUrl : System.getProperty("base.url", "http://localhost:8080");
        String key = apiKey != null ? apiKey : System.getenv("API_KEY");
        return new ApiClient(url, key);
    }
}
```

## 4. Add scenario-scoped bindings

Bindings created fresh for each scenario go in a separate module, with `@ScenarioScope` applied to the `@Provides` methods in that module.

```java
package com.example.tests;

import dagger.Module;
import dagger.Provides;
import dev.joss.dagger.cucumber.api.ScenarioScope;

@Module
public final class ScenarioModule {

    @Provides
    @ScenarioScope
    static Basket provideBasket(ApiClient apiClient) {
        return new Basket(apiClient);
    }
}
```

List `ScenarioModule.class` alongside your root modules in `@Component(modules = {...})`. The processor moves scoped modules to the generated subcomponent automatically.

## 5. Write a step definition

Any class with an `@Inject` constructor in the root component's package tree is discovered automatically.

```java
package com.example.tests;

import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
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

## 6. Configure JUnit Platform

```properties
# src/test/resources/junit-platform.properties
cucumber.glue=com.example.tests
cucumber.publish.quiet=true
```

## 7. Create the suite runner

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

## 8. Run

```
./gradlew test
```

## Next steps

- [configuration-reference.md](configuration-reference.md) — full annotation and rule reference
- [scenario-scope.md](scenario-scope.md) — sharing state between step definitions
- [providing-static-values.md](providing-static-values.md) — injecting env vars and the `Scenario` object
- [architecture.md](architecture.md) — how the processor and runtime fit together
