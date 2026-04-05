# cucumber-dagger

Runtime module for `dagger-cucumber`. Implements the Cucumber SPI to integrate Dagger 2 dependency injection with Cucumber JVM.

## What this module provides

- **`DaggerObjectFactory`** — `io.cucumber.core.backend.ObjectFactory` implementation; creates per-scenario Dagger subcomponents, resolves step definitions and scoped objects via pre-computed `MethodHandle`s
- **`DaggerBackend`** — `io.cucumber.core.backend.BackendProviderService` implementation; locates the generated root component via `ServiceLoader` and wires it into the object factory at `loadGlue()` time
- **`ObjectFactoryHolder`** — thread-safe static registry that bridges the independently ServiceLoader-created `DaggerObjectFactory` and `DaggerBackend`
- **Public API annotations and interfaces** in `dev.joss.dagger.cucumber.api`:
  - `@CucumberDaggerConfiguration` — marks the root Dagger component
  - `@ScenarioScoped` — scope annotation for per-scenario objects
  - `CucumberDaggerComponent` — marker interface extended by the generated wrapper component
  - `ScenarioScopedComponent` — marker interface for the per-scenario subcomponent

## Dependency

```kotlin
testImplementation("dev.joss:cucumber-dagger")
// or via BOM:
testImplementation(platform("dev.joss:cucumber-dagger-bom:<version>"))
testImplementation("dev.joss:cucumber-dagger")
```

This module must be accompanied by the annotation processor at compile time. See the [root README](../README.md) or the [Getting Started guide](../docs/getting-started.md) for full setup instructions.

## Further reading

- [Architecture](../docs/architecture.md) — how the runtime SPI bridge works
- [Scenario Scopes](../docs/scenario-scopes.md) — the `@ScenarioScoped` lifecycle
- [Configuration Reference](../docs/configuration-reference.md) — full API reference
