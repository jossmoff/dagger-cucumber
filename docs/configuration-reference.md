# Configuration Reference

This page documents every public API element in `dagger-cucumber`: annotations, interfaces, and the `junit-platform.properties` key that wires the glue package.

---

## Annotations

### `@CucumberDaggerConfiguration`

```java
package dev.joss.dagger.cucumber.api;
```

**Purpose:** Marks a Dagger `@Component` interface as the root component for a Cucumber test suite. Triggers the annotation processor.

**Target:** `ElementType.TYPE` (interfaces only)

**Retention:** `RUNTIME`

**Rules:**

| Rule | Compile error if violated |
|------|--------------------------|
| Exactly one interface in the test classpath must carry this annotation | `"Only one @CucumberDaggerConfiguration is allowed"` |
| The annotated element must be an `interface`, not a class | `"@CucumberDaggerConfiguration can only be applied to interfaces"` |

**What you need to declare:**

- Your own application modules in `@Component(modules = …)`.
- Any scope annotation (e.g. `@Singleton`) that should be carried through to the generated wrapper component.

**What you do NOT need to declare:**

- `CucumberDaggerModule` in `modules` — the processor adds it automatically.
- Extending `CucumberDaggerComponent` — the generated wrapper component does this.

**Minimal example:**

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {ServiceModule.class})
public interface AppComponent {}
```

---

### `@ScenarioScoped`

```java
package dev.joss.dagger.cucumber.api;
```

**Purpose:** Dagger scope annotation that binds the lifetime of an object to a single Cucumber scenario. A new instance is created before each scenario starts and discarded when it ends.

**Target:** `ElementType.TYPE` and `ElementType.METHOD`

**Retention:** `RUNTIME`

**Meta-annotations:** `@jakarta.inject.Scope`, `@Documented`

**Two usage styles:**

#### Style A — annotate the class

Apply directly to a concrete class with an `@Inject` constructor.

```java
@ScenarioScoped
public final class Cart {
    @Inject
    public Cart(ProductCatalogue catalogue) { ... }
}
```

Requirements:
- The class must be **concrete** (not abstract, not an interface, not an enum).
- It must declare exactly **one** `@Inject` constructor.
- It must be in the **glue package** (the package declared in `cucumber.glue`).

Compile errors emitted if violated:
- `"@ScenarioScoped can only be applied to concrete classes or @Provides methods"` — if applied to an interface, abstract class, or enum
- `"@ScenarioScoped class Foo must declare an @Inject constructor …"` — if the `@Inject` constructor is missing

#### Style B — `@Provides @ScenarioScoped` on a module method

Use when you cannot annotate the class directly — third-party types, factory logic, or test-environment-only bindings.

```java
@Module
public abstract class TestModule {

    @Provides
    @ScenarioScoped
    static HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }
}
```

Requirements:
- The module must be listed in the root `@Component(modules = …)`.
- The method must carry **both** `@Provides` and `@ScenarioScoped`.
- The method must **not** carry a qualifier annotation (e.g. `@Named`).

Compile error emitted if violated:
- `"Qualified @ScenarioScoped provider methods are not currently supported: methodName"`

See [Scenario Scopes](scenario-scopes.md) for a full explanation of both styles and their lifecycle.

---

## Interfaces

### `CucumberDaggerComponent`

```java
package dev.joss.dagger.cucumber.api;
```

Marker interface that the generated wrapper component (`GeneratedCucumberAppComponent`) extends. It is **not** intended to be extended by user code.

```java
public interface CucumberDaggerComponent {
    @SuppressWarnings("rawtypes")
    ScenarioScopedComponent.Builder scopedComponentBuilder();
}
```

**`scopedComponentBuilder()`** — returns a builder for the per-scenario `ScenarioScopedComponent`. The concrete binding is provided by the generated `CucumberDaggerModule`. The runtime calls this method before each scenario to obtain a fresh subcomponent.

---

### `ScenarioScopedComponent`

```java
package dev.joss.dagger.cucumber.api;
```

Marker interface for the per-scenario Dagger subcomponent. The processor generates a concrete subinterface (`GeneratedScopedComponent`) that extends this interface and declares provision methods for every `@ScenarioScoped` type and every step-definition class in the glue package.

**User code does not implement or reference this interface directly.**

```java
public interface ScenarioScopedComponent {
    interface Builder<T extends ScenarioScopedComponent> {
        T build();
    }
}
```

**`Builder<T>`** — Dagger generates a concrete implementation of this builder for the generated subcomponent. The runtime uses it to call `.build()` before each scenario.

---

## `junit-platform.properties`

The standard Cucumber configuration file, placed in `src/test/resources`.

| Key | Required | Description |
|-----|----------|-------------|
| `cucumber.glue` | **Yes** | Package (or comma-separated list of packages) where Cucumber looks for step definitions and hook classes. `@ScenarioScoped` Style-A classes must be in this package. |
| `cucumber.publish.quiet` | No | Set to `true` to suppress the Cucumber publish reminder banner. |

**Example:**

```properties
cucumber.glue=com.example
cucumber.publish.quiet=true
```

> The glue package also determines which `@ScenarioScoped`-annotated classes the processor picks up. Classes outside the configured glue package are silently ignored by the processor.

---

## Generated API (reference only)

The following types are generated by the processor. They are part of the runtime contract between the processor output and the runtime SPI, but you will not normally reference them in application code.

| Generated type | Located in | Purpose |
|----------------|-----------|---------|
| `GeneratedScopedComponent` | Same package as your root component | Per-scenario `@Subcomponent`; extends `ScenarioScopedComponent` |
| `GeneratedScopedModule` | Same package as your root component | `@Module` for `GeneratedScopedComponent`; includes Style-B user modules |
| `CucumberDaggerModule` | Same package as your root component | Declares `GeneratedScopedComponent` as a subcomponent; provides `ScenarioScopedComponent.Builder` |
| `GeneratedCucumberAppComponent` | Same package as your root component | Wrapper `@Component` combining your modules with `CucumberDaggerModule`; extends `CucumberDaggerComponent` |
| `ScenarioScopedComponentAccessor` | `dev.joss.dagger.cucumber.generated` | Returns `GeneratedScopedComponent.class`; used by the runtime as a fast-path alternative to a classpath scan |
| `META-INF/services/…CucumberDaggerComponent` | Resources root | ServiceLoader entry pointing to `DaggerGeneratedCucumberAppComponent` |

See [Architecture](architecture.md) for code samples and a full explanation of each file.

---

## Further reading

- [Getting Started](getting-started.md) — installation and a minimal working example
- [Scenario Scopes](scenario-scopes.md) — full lifecycle and isolation semantics for `@ScenarioScoped`
- [Architecture](architecture.md) — how the processor and runtime work together
- [Troubleshooting](troubleshooting.md) — compile-time and runtime error messages with remedies
