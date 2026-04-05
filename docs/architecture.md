# Architecture

This document describes how `dagger-cucumber` works at the runtime and compile-time levels — the Cucumber SPI bridge, the annotation-processor pipeline, and every file that is generated.

## Overview

`dagger-cucumber` has two distinct layers:

| Layer | Module | Runs at |
|-------|--------|---------|
| **Annotation processor** | `cucumber-dagger-processor` | Compile time |
| **Runtime** | `cucumber-dagger` | Test execution time |

At **compile time**, the processor inspects your `@CucumberDaggerConfiguration` component, collects your scenario-scoped classes and step definitions, and generates a complete set of Dagger source files.

At **runtime**, the generated Dagger component is located via Java's [ServiceLoader](https://docs.oracle.com/en/java/docs/api/java.base/java/util/ServiceLoader.html), the component graph is wired into Cucumber's SPI lifecycle, and fresh per-scenario subcomponents are created and destroyed around each scenario.

---

## Runtime: the Cucumber SPI bridge

Cucumber discovers backend and object-factory implementations via [Java's `ServiceLoader` mechanism](https://docs.oracle.com/en/java/docs/api/java.base/java/util/ServiceLoader.html). `dagger-cucumber` registers two SPI implementations:

| SPI interface | Implementation | Service file |
|---------------|---------------|-------------|
| `io.cucumber.core.backend.BackendProviderService` | `DaggerBackendProviderService` | `META-INF/services/io.cucumber.core.backend.BackendProviderService` |
| `io.cucumber.core.backend.ObjectFactory` | `DaggerObjectFactory` | `META-INF/services/io.cucumber.core.backend.ObjectFactory` |

Cucumber's ServiceLoader instantiates these **independently** — there is no built-in channel for them to share state. `ObjectFactoryHolder` is a `volatile` static registry that allows `DaggerObjectFactory` (constructed first) to make itself available to `DaggerBackend` when `loadGlue()` is called.

### Lifecycle sequence

```
Cucumber starts
│
├─ ServiceLoader creates DaggerObjectFactory
│   └─ registers itself in ObjectFactoryHolder
│
├─ ServiceLoader creates DaggerBackend (via DaggerBackendProviderService)
│
├─ loadGlue() [once, before any scenarios]
│   ├─ reads META-INF/services/…CucumberDaggerComponent → finds DaggerGeneratedCucumberAppComponent
│   ├─ calls DaggerGeneratedCucumberAppComponent.create() → root component instance
│   ├─ loads GeneratedScopedComponent class (via ScenarioScopedComponentAccessor fast path)
│   └─ calls DaggerObjectFactory.configure(root, scopedInterface)
│       ├─ reflects root component interface → builds MethodHandle map for root provision methods
│       └─ reflects GeneratedScopedComponent interface → builds MethodHandle map for scoped methods
│
│  [for each scenario]
├─ buildWorld()
│   └─ DaggerObjectFactory.buildWorld()
│       ├─ calls root.scopedComponentBuilder().build() → fresh GeneratedScopedComponent
│       └─ binds scoped MethodHandles to the new subcomponent instance
│
├─ [scenario runs — step defs resolved via getInstance()]
│   └─ DaggerObjectFactory.getInstance(Class)
│       ├─ checks scoped suppliers first (scenario-scoped objects + step defs)
│       └─ falls back to root handles (singletons)
│
└─ disposeWorld()
    └─ DaggerObjectFactory.disposeWorld()
        └─ clears per-scenario supplier map and instance cache (ThreadLocal.remove())
```

### Why MethodHandles?

Rather than calling Dagger's generated component methods via reflection on every `getInstance()` call, `DaggerObjectFactory.configure()` pre-computes [`MethodHandle`](https://docs.oracle.com/en/java/docs/api/java.base/java/lang/invoke/MethodHandle.html) objects for each provision method once, at startup. Subsequent `getInstance()` calls invoke the cached handle directly, avoiding per-call reflective lookup overhead.

---

## Compile time: the annotation-processor pipeline

The processor is triggered by the `@CucumberDaggerConfiguration` annotation. It runs a four-step immutable pipeline, short-circuiting on the first step that reports a compile error.

```
Set<Element> (annotated with @CucumberDaggerConfiguration)
    │
    ▼
┌─────────────────────┐
│ FindRootComponentStep│  validates: exactly 1 element, must be interface
└──────────┬──────────┘
           │ FoundRootComponent
           ▼
┌───────────────────────────┐
│ CollectScopedClassesStep  │  finds @ScenarioScoped classes in glue package
└──────────┬────────────────┘  validates: concrete, @Inject constructor present
           │ CollectedScopedClasses
           ▼
┌───────────────────────────┐
│ CollectStepDefsStep       │  finds classes with @Inject constructors (not @ScenarioScoped)
└──────────┬────────────────┘  in the glue package
           │ CollectedStepDefs
           ▼
┌───────────────────────────┐
│ BuildProcessingModelStep  │  assembles Style-A + Style-B provision methods,
└──────────┬────────────────┘  copies scope annotations, rejects qualified Style-B providers
           │ ProcessingModel
           ▼
     CucumberDaggerGenerator  →  6 generated files
```

Each step is a `ProcessingStep<C, I, O>` functional interface. The `Pipeline<C, T>` runner threads a shared `ProcessingContext` (containing the `ProcessingEnvironment`, `RoundEnvironment`, and pre-looked-up `TypeElement`s) through each step, returning a `StepResult<T>` that is either `succeeded(value)` or `failed()`. A failure short-circuits all remaining steps.

---

## Generated files

Given a root component named `AppComponent` in package `com.example`, the processor generates the following files (all in `com.example` unless noted):

### 1. `GeneratedScopedModule`

```java
@Module
public abstract class GeneratedScopedModule {}
// or, if Style-B modules are present:
@Module(includes = {MyModule.class})
public abstract class GeneratedScopedModule {}
```

A Dagger `@Module` used by `GeneratedScopedComponent`. If the user has any modules containing `@Provides @ScenarioScoped` methods (Style B), those modules are listed in `includes` so Dagger picks up the bindings.

### 2. `GeneratedScopedComponent`

```java
@ScenarioScoped
@Subcomponent(modules = GeneratedScopedModule.class)
public interface GeneratedScopedComponent extends ScenarioScopedComponent {

    // one method per @ScenarioScoped class (Style A)
    Basket basket();
    Discount discount();

    // one method per step-definition class
    CheckoutSteps checkoutSteps();

    @Subcomponent.Builder
    interface Builder extends ScenarioScopedComponent.Builder<GeneratedScopedComponent> {}
}
```

The per-scenario Dagger subcomponent. A fresh instance is created before each scenario and discarded afterwards. Every `@ScenarioScoped` class and every step definition class has a provision method here so the runtime can resolve them via `MethodHandle`.

### 3. `CucumberDaggerModule`

```java
/**
 * Generated by cucumber-dagger-processor.
 * This module is included automatically by the generated wrapper component;
 * you do not need to add it to your @Component modules list.
 */
@Module(subcomponents = GeneratedScopedComponent.class)
public abstract class CucumberDaggerModule {

    @Provides
    @SuppressWarnings("rawtypes")
    public static ScenarioScopedComponent.Builder provideScopedBuilder(
            GeneratedScopedComponent.Builder builder) {
        return builder;
    }
}
```

Declares `GeneratedScopedComponent` as a Dagger subcomponent and provides the raw-type `ScenarioScopedComponent.Builder` binding. This is what makes `CucumberDaggerComponent.scopedComponentBuilder()` resolvable at runtime.

### 4. `GeneratedCucumberAppComponent`

```java
@Singleton
@Component(modules = {ServiceModule.class, CucumberDaggerModule.class})
public interface GeneratedCucumberAppComponent extends CucumberDaggerComponent {}
```

A wrapper `@Component` that:
- Extends `CucumberDaggerComponent` (required by the runtime)
- Combines your application modules with the generated `CucumberDaggerModule`
- Carries the same scope annotations (e.g. `@Singleton`) as your root component

Users do **not** need to extend `CucumberDaggerComponent` themselves or include `CucumberDaggerModule` — the processor handles both.

### 5. `ScenarioScopedComponentAccessor` _(in `dev.joss.dagger.cucumber.generated`)_

```java
public final class ScenarioScopedComponentAccessor {
    public Class<? extends ScenarioScopedComponent> getScopedComponentClass() {
        return GeneratedScopedComponent.class;
    }
}
```

A lightweight accessor that returns the `GeneratedScopedComponent` class literal. The runtime loads this class first (fast path) to avoid a classpath scan at startup.

### 6. `META-INF/services/dev.joss.dagger.cucumber.api.CucumberDaggerComponent`

```
com.example.DaggerGeneratedCucumberAppComponent
```

The Java [ServiceLoader](https://docs.oracle.com/en/java/docs/api/java.base/java/util/ServiceLoader.html) entry pointing to Dagger's generated factory for the wrapper component. The runtime reads this file to locate and call `DaggerGeneratedCucumberAppComponent.create()`.

---

## Component hierarchy

```
          ┌──────────────────────────────────────────────┐
          │  GeneratedCucumberAppComponent               │
          │  @Singleton @Component                       │
          │  modules: [ServiceModule, CucumberDaggerModule]│
          │                                              │
          │  provision methods:                          │
          │    ScenarioScopedComponent.Builder           │
          │      scopedComponentBuilder()                │
          └────────────────────┬─────────────────────────┘
                               │ creates (per scenario)
                               ▼
          ┌──────────────────────────────────────────────┐
          │  GeneratedScopedComponent                    │
          │  @ScenarioScoped @Subcomponent               │
          │  modules: [GeneratedScopedModule]            │
          │                                              │
          │  provision methods:                          │
          │    Basket basket()                           │
          │    Discount discount()                       │
          │    CheckoutSteps checkoutSteps()             │
          └──────────────────────────────────────────────┘
```

Singletons (e.g. `PriceList`) live in the root component and are injected into both scoped objects and step definitions by Dagger's normal subcomponent scoping rules.

---

## Key design decisions

| Decision | Rationale |
|----------|-----------|
| SPI bridge via `ObjectFactoryHolder` | Cucumber's ServiceLoader creates the `Backend` and `ObjectFactory` independently with no injection channel between them |
| `volatile` static field in `ObjectFactoryHolder` | Ensures cross-thread visibility if Cucumber initialises backends on a different thread |
| Pre-computed `MethodHandle`s | Avoids per-`getInstance()` reflective lookup; handles are computed once during `loadGlue()` |
| Accessor class (`ScenarioScopedComponentAccessor`) | The generated subcomponent type is in the user's package; the accessor gives the runtime a deterministic, fast way to retrieve its `Class<?>` without a classpath scan |
| Per-scenario `ThreadLocal` for instance cache | Supports potential future parallel scenario execution; each thread sees its own scenario state |
| Immutable pipeline with `StepResult` | Keeps each processing step independently testable and makes short-circuit failure explicit |

---

## Further reading

- [Scenario Scopes](scenario-scopes.md) — the `@ScenarioScoped` lifecycle in detail
- [Configuration Reference](configuration-reference.md) — all public API annotations and interfaces
- [Troubleshooting](troubleshooting.md) — error messages and remedies
- [Dagger documentation](https://dagger.dev/dev-guide/) — subcomponents, scopes, and modules
- [Cucumber JVM SPI](https://github.com/cucumber/cucumber-jvm/tree/main/cucumber-core/src/main/java/io/cucumber/core/backend) — the Backend and ObjectFactory SPI interfaces
