# Architecture

cucumber-dagger connects Cucumber's Backend and ObjectFactory service-provider interfaces to a Dagger component graph. Understanding how the three layers fit together helps when you need to debug unexpected behaviour or extend the library.

## Overview

The library is split into three layers:

1. **Compile-time** - the annotation processor (`cucumber-dagger-processor`) reads your `@CucumberDaggerConfiguration` interface and emits Java source files that encode the component hierarchy.
2. **Generated code** - Dagger's own compiler reads the generated sources and produces the final `Dagger*` implementation classes that do the actual injection.
3. **Runtime** - `DaggerBackend` and `DaggerObjectFactory` implement the Cucumber SPIs and orchestrate the component lifecycle during a test run.

## Module structure

| Module                      | Published artefact                   | Purpose                                                                                                                                     |
|-----------------------------|--------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `cucumber-dagger`           | `dev.joss:cucumber-dagger`           | Runtime API annotations (`@CucumberDaggerConfiguration`, `@ScenarioScope`) and SPI implementations (`DaggerBackend`, `DaggerObjectFactory`) |
| `cucumber-dagger-processor` | `dev.joss:cucumber-dagger-processor` | Annotation processor that generates Dagger wiring at compile time                                                                           |
| `cucumber-dagger-bom`       | `dev.joss:cucumber-dagger-bom`       | Version management BOM that aligns `cucumber-dagger` and `cucumber-dagger-processor`                                                        |

## The annotation processor

The processor triggers on any type annotated with `@CucumberDaggerConfiguration`. It runs as a standard Java annotation processor and writes six artefacts.

### GeneratedScopedModule

A Dagger `@Module` that lists all of the user's scoped modules (those containing `@Provides @ScenarioScope` methods) in its `includes` attribute. This exists so the subcomponent receives the scoped bindings without the user having to know about the internal subcomponent type.

### GeneratedScopedComponent

A `@ScenarioScope @Subcomponent` with one provision method per scenario-scoped type and one per step-definition class. This is the per-scenario Dagger component. Dagger creates a new instance of it before each scenario and discards it afterwards. It also contains an inner `Builder` interface annotated with `@Subcomponent.Builder`.

### CucumberDaggerModule

A `@Module` that declares `GeneratedScopedComponent` as a subcomponent and provides the `ScenarioScopedComponent.Builder` binding that bridges the generated builder type to the `CucumberDaggerComponent` interface method used by the runtime.

### GeneratedCucumber{Name}

A wrapper `@Component` interface named `GeneratedCucumber` followed by the simple name of your root component interface (for example, `GeneratedCucumberIntegrationTestConfig`). It:

- extends your root component interface so Dagger generates implementations for any provision methods you declared there
- extends `CucumberDaggerComponent` so the runtime can call `scopedComponentBuilder()` without knowing the concrete type
- carries the same scope annotations (for example, `@Singleton`) as your root interface
- lists your non-scoped modules plus the generated `CucumberDaggerModule`

Dagger's compiler processes this interface and produces `DaggerGeneratedCucumberIntegrationTestConfig` with a static `create()` method.

### GeneratedComponentResolver

Generated in the fixed package `dev.joss.dagger.cucumber.generated`, this class implements `ComponentResolver` without any reflection. It replaces what would otherwise be a reflective `type == X.class` lookup with a plain `if`-chain over all known types. Three methods are generated:

- `createScoped(root)` - calls `root.scopedComponentBuilder().build()` to produce a new subcomponent
- `resolveScoped(type, scoped)` - casts `scoped` to `GeneratedScopedComponent` and dispatches to the appropriate provision method
- `resolveRoot(type, root)` - casts `root` to your root component interface and dispatches to any provision methods you declared on it

The runtime loads this class by its fixed name, so it is always found even though it lives in a different package from the generated component.

### Service file

`META-INF/services/dev.joss.dagger.cucumber.api.CucumberDaggerComponent` is written with a single entry pointing to `DaggerGeneratedCucumber{Name}`. The runtime reads this file at startup to locate the root component factory.

## The Dagger component hierarchy

```
DaggerGeneratedCucumberIntegrationTestConfig   ← @Singleton, lives for the whole test run
│  implements CucumberDaggerComponent
│  implements IntegrationTestConfig (your interface)
│
└── GeneratedScopedComponent          ← @ScenarioScope, created per scenario
       implements ScenarioScopedComponent
       provision methods for every @ScenarioScope type
       provision methods for every step-definition class
```

The root component holds all `@Singleton` bindings. The subcomponent inherits access to them and additionally owns the `@ScenarioScope` bindings. Step definitions are provision methods on the subcomponent, so they receive both singleton and scenario-scoped dependencies.

## The runtime lifecycle

`DaggerBackend` implements Cucumber's `Backend` SPI and `DaggerObjectFactory` implements the `ObjectFactory` SPI. Both are registered via `META-INF/services` entries and loaded by Cucumber's `ServiceLoader` mechanism.

The sequence during a test run is:

1. **`loadGlue()` - once before any scenarios run**
   - `DaggerBackend` reads the service file and reflectively calls `DaggerGeneratedCucumber{Name}.create()` to obtain the root component.
   - It loads `GeneratedComponentResolver` by its fixed class name.
   - It hands both to `DaggerObjectFactory.configure()`.

2. **`buildWorld()` - before each scenario**
   - `DaggerBackend` calls `DaggerObjectFactory.buildWorld()`.
   - `DaggerObjectFactory` calls `resolver.createScoped(root)`, which calls `root.scopedComponentBuilder().build()` to produce a fresh subcomponent stored in a `ThreadLocal`.

3. **`getInstance(type)` - for every step definition Cucumber needs**
   - `DaggerObjectFactory` calls `resolver.resolveScoped(type, scoped)` first.
   - If that returns `null`, it falls back to `resolver.resolveRoot(type, root)`.
   - If both return `null`, it throws `IllegalStateException`.

4. **`disposeWorld()` - after each scenario**
   - `DaggerObjectFactory` removes the `ThreadLocal` entry, making the subcomponent eligible for garbage collection.

The `ThreadLocal` in `DaggerObjectFactory` means the framework is safe for parallel scenario execution at the thread level, provided your own bindings are also thread-safe.

## Step definition discovery

The processor collects step-definition classes by scanning all types with an `@Inject`-annotated constructor whose package name starts with the root component's package. This means any class in `com.example.tests` or any sub-package (for example, `com.example.tests.checkout`) is discovered automatically. No additional annotation is needed.

For each discovered class, the processor generates a provision method on `GeneratedScopedComponent` and a corresponding dispatch branch in `GeneratedComponentResolver.resolveScoped`. At runtime, Cucumber calls `getInstance(CheckoutSteps.class)` and the resolver returns the Dagger-managed instance.
