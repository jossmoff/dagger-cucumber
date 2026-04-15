# Providing static values and accessing the Scenario object

## The lifecycle constraint

Cucumber calls `DaggerBackend.buildWorld()` before it processes any `@Before` hooks. Because `buildWorld()` accepts no parameters (it is defined that way by the Cucumber `Backend` SPI), the `io.cucumber.java.Scenario` object is not available at the moment the scenario-scoped Dagger subcomponent is created. You cannot inject `Scenario` directly into a constructor or a `@Provides` method.

## The ScenarioHolder pattern

The solution is a mutable holder object that is created once at singleton scope and populated by a `@Before` hook after Cucumber has made the `Scenario` available.

### Step 1: create the holder

```java
package com.example.tests;

import io.cucumber.java.Scenario;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class ScenarioHolder {

    private Scenario scenario;

    @Inject
    public ScenarioHolder() {}

    public void set(Scenario scenario) {
        this.scenario = scenario;
    }

    public Scenario get() {
        if (scenario == null) {
            throw new IllegalStateException(
                    "ScenarioHolder has not been populated. "
                    + "Ensure your @Before hook calls ScenarioHolder.set().");
        }
        return scenario;
    }
}
```

`ScenarioHolder` has an `@Inject` constructor, so it is discovered as a step definition class and its provision method is generated on the scenario subcomponent. It is bound at `@Singleton` scope via a `@Provides` method in a root module, so all injection sites receive the same instance.

Alternatively, bind it explicitly in a root module:

```java
@Module
public abstract class AppModule {

    @Provides
    @Singleton
    static ScenarioHolder provideScenarioHolder() {
        return new ScenarioHolder();
    }
}
```

### Step 2: populate the holder in a @Before hook

```java
package com.example.tests;

import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import jakarta.inject.Inject;

public final class ScenarioHooks {

    private final ScenarioHolder scenarioHolder;

    @Inject
    public ScenarioHooks(ScenarioHolder scenarioHolder) {
        this.scenarioHolder = scenarioHolder;
    }

    @Before
    public void captureScenario(Scenario scenario) {
        scenarioHolder.set(scenario);
    }
}
```

Cucumber calls `@Before` hooks after `buildWorld()` but before any step methods, so `scenarioHolder` is already populated by the time your step methods run.

### Step 3: inject ScenarioHolder where you need the Scenario object

```java
package com.example.tests;

import io.cucumber.java.en.Then;
import jakarta.inject.Inject;

public final class AuditSteps {

    private final ScenarioHolder scenarioHolder;

    @Inject
    public AuditSteps(ScenarioHolder scenarioHolder) {
        this.scenarioHolder = scenarioHolder;
    }

    @Then("the scenario name is logged")
    public void theScenarioNameIsLogged() {
        String name = scenarioHolder.get().getName();
        // ... use name
    }
}
```

## Thread safety note

The `ScenarioHolder` shown above stores the `Scenario` reference in a plain instance field. This is safe for sequential scenario execution but is **not thread-safe for parallel execution**. If you run scenarios in parallel across threads, replace the field with a `ThreadLocal<Scenario>` or an `AtomicReference<Scenario>`:

```java
// Thread-safe variant using ThreadLocal
@Singleton
public final class ScenarioHolder {

    private final ThreadLocal<Scenario> holder = new ThreadLocal<>();

    @Inject
    public ScenarioHolder() {}

    public void set(Scenario scenario) {
        holder.set(scenario);
    }

    public Scenario get() {
        Scenario s = holder.get();
        if (s == null) {
            throw new IllegalStateException("ScenarioHolder has not been populated.");
        }
        return s;
    }

    public void clear() {
        holder.remove();
    }
}
```

Call `scenarioHolder.clear()` from an `@After` hook to prevent `ThreadLocal` leaks when using a thread pool.

## Static configuration values

Values that are known before the test run - such as the target environment name, a base URL, or feature flags - do not need the `ScenarioHolder` pattern. Provide them via a `@Provides @Singleton` method in a root module.

```java
// A typed wrapper avoids the ambiguity of injecting bare String values.
public record BaseUrl(String value) {}
```

```java
@Module
public abstract class EnvironmentModule {

    @Provides
    @Singleton
    static BaseUrl provideBaseUrl() {
        String env = System.getenv("TEST_ENV");
        return switch (env != null ? env : "local") {
            case "staging" -> new BaseUrl("https://staging.example.com");
            case "prod"    -> new BaseUrl("https://example.com");
            default        -> new BaseUrl("http://localhost:8080");
        };
    }
}
```

Step definitions can then declare `BaseUrl` as a constructor parameter and receive the configured value for the entire test run. Using a typed wrapper rather than a bare `String` avoids ambiguity when multiple `String` values exist in the graph.

## Note on future @BindsInstance support

The current implementation calls `DaggerGeneratedCucumber{Name}.create()` to construct the root component. Dagger only generates a no-argument `create()` method when all modules have no-argument constructors or static `@Provides` methods and no `@BindsInstance` parameters are required on the component builder.

If you want to pass caller-supplied values (for example, a `Scenario` object) into the scenario component at construction time, the `ComponentResolver.createScoped()` method would need to accept additional parameters and the generated subcomponent builder would need `@BindsInstance` methods. This is not yet implemented. You can track progress and contribute to the discussion on the [GitHub issues page](https://github.com/jossmoff/dagger-cucumber/issues).
