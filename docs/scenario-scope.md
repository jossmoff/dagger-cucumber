# Scenario scope

`@ScenarioScope` bindings are created once per scenario and discarded when it ends. The next scenario gets fresh instances.

| Scope | Lifetime | Annotation |
|---|---|---|
| `@Singleton` | Entire test run | On `@Provides` method or component |
| `@ScenarioScope` | Single scenario | On `@Provides` method only |

## Declaring a scoped binding

Add `@Provides @ScenarioScope` to a method in a `@Module` and list that module on your root `@Component`. The processor moves it to the generated subcomponent automatically.

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

## Sharing state between step definitions

Two step definition classes injecting the same `@ScenarioScope` type receive the **same instance** within one scenario. Mutations in one class are visible in another.

```java
// Both receive the same Basket instance for the current scenario
public final class AddItemSteps {
    @Inject AddItemSteps(Basket basket) { ... }
}

public final class TotalSteps {
    @Inject TotalSteps(Basket basket) { ... }
}
```

## Cross-scope dependencies

A `@ScenarioScope` binding may depend on a `@Singleton` binding. The reverse is not valid — Dagger reports a scope violation at compile time.

```mermaid
graph LR
    S["@Singleton — PriceList"] --> B["@ScenarioScope — Basket"]
```

## Parallel execution

cucumber-dagger supports **thread-level parallelism** (Cucumber's default parallel model).

Enable it in `src/test/resources/junit-platform.properties`:

```properties
cucumber.execution.parallel.enabled=true
cucumber.execution.parallel.config.strategy=fixed
cucumber.execution.parallel.config.fixed.parallelism=4
```

**Why it is safe:** `DaggerObjectFactory` stores the per-scenario subcomponent in a `ThreadLocal`. When Cucumber dispatches a scenario to a worker thread, `buildWorld()` writes to that thread's slot and `disposeWorld()` clears it. Concurrent scenarios on different threads each see their own `ScenarioScopedComponent` and cannot observe each other's state.

**`@Singleton` bindings** are shared across all threads (and all scenarios) for the lifetime of the test run. This is correct and expected — singletons are inherently shared. Ensure that any singleton you provide is thread-safe if it carries mutable state.

**`ObjectFactoryHolder` constraint:** `ObjectFactoryHolder` is a single-JVM-wide registry. It holds a reference to the single `DaggerObjectFactory` that Cucumber creates via the `ObjectFactory` SPI — there is always exactly one factory per test run. Parallel scenarios all share this factory, which is safe because the per-scenario state is in `ThreadLocal` fields. Do not attempt to run multiple independent Cucumber suites in the same JVM concurrently.

## Constraints

| Constraint | Detail |
|---|---|
| No qualifiers | `@ScenarioScope` + `@Named` on the same method → compile error |
| Method-level only | Annotating a class with `@ScenarioScope` has no effect; use `@Provides` methods |
| One suite per JVM | Running multiple independent Cucumber test runs concurrently in the same JVM is not supported (`ObjectFactoryHolder` is a JVM-wide singleton) |
