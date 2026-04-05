# Scenario Scopes

`@ScenarioScoped` is a [Dagger scope annotation](https://dagger.dev/dev-guide/scope) that binds the lifetime of an object to a single Cucumber scenario. A fresh instance is created just before each scenario starts and discarded when it ends.

This document covers both ways to declare a scenario-scoped binding, the full object lifecycle, isolation guarantees, and patterns for sharing state across step definitions.

---

## What `@ScenarioScoped` means

In Dagger, a scope annotation on a binding tells Dagger to create the object at most once within the lifetime of the component that carries that scope. For `@ScenarioScoped`, that component is the generated `GeneratedScopedComponent` subcomponent, which is re-created fresh for every scenario.

```
Before scenario  →  new GeneratedScopedComponent()
  Within scenario  →  Dagger creates @ScenarioScoped objects at most once (cached by subcomponent)
After scenario   →  subcomponent discarded, all scoped objects eligible for GC
```

Objects annotated with `@Singleton` (or any root-component scope) live in the parent component and **are not re-created** between scenarios.

---

## Style A — annotate the class

The simplest way to declare a scenario-scoped binding is to annotate the class directly.

### Requirements

1. The class must be **concrete** (not abstract, not an interface, not an enum).
2. It must declare exactly one constructor annotated with `@Inject`.
3. It must be in the **glue package** (the package declared in `cucumber.glue`).

### Example

```java
@ScenarioScoped
public final class Cart {

    private final List<String> items = new ArrayList<>();
    private final ProductCatalogue catalogue;

    @Inject
    public Cart(ProductCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public void add(String product) { items.add(product); }
    public int size() { return items.size(); }
    public int total() {
        return items.stream().mapToInt(catalogue::priceOf).sum();
    }
}
```

The annotation processor discovers `Cart` during compilation and generates a provision method for it on `GeneratedScopedComponent`:

```java
// generated
public interface GeneratedScopedComponent extends ScenarioScopedComponent {
    Cart cart();
    // ...
}
```

### Dependencies of scoped classes

A `@ScenarioScoped` class can depend on:

| Dependency type | Example | How it is resolved |
|-----------------|---------|-------------------|
| Singleton / root-scoped | `ProductCatalogue` | Inherited from the parent component — Dagger's normal subcomponent scoping |
| Other `@ScenarioScoped` objects | `Discount` | Injected from the same subcomponent instance — same scenario |
| Unscoped (`@Inject` constructor, no scope) | Any plain class | Created fresh on every injection point by default |

---

## Style B — `@Provides @ScenarioScoped` on a module method

Use Style B when you cannot annotate the class directly — for example, a third-party type, a type that needs factory logic, or a binding that should only exist in the test environment.

### Requirements

1. A `@Module` class must be listed in your root `@Component(modules = …)`.
2. A method in that module must be annotated with **both** `@Provides` and `@ScenarioScoped`.
3. The method must **not** carry a qualifier annotation (e.g. `@Named`) — qualified scoped providers are not currently supported.

### Example

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

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {TestModule.class})
public interface AppComponent {}
```

The processor detects `TestModule` as a "user scoped module" and includes it in `GeneratedScopedModule`:

```java
// generated
@Module(includes = {TestModule.class})
public abstract class GeneratedScopedModule {}
```

A provision method for `HttpClient` is also added to `GeneratedScopedComponent`:

```java
// generated
HttpClient httpClient();
```

---

## Lifecycle in detail

The table below maps Cucumber's lifecycle callbacks to the `@ScenarioScoped` object lifecycle:

| Cucumber event | `dagger-cucumber` action | Effect on `@ScenarioScoped` objects |
|----------------|--------------------------|--------------------------------------|
| Before first scenario | `loadGlue()` | Root component created; no scoped objects yet |
| Before each scenario | `buildWorld()` | Fresh `GeneratedScopedComponent` created; scoped bindings reset |
| Step execution | `getInstance(Class)` | Scoped object created on first access, then cached for the rest of the scenario |
| After each scenario | `disposeWorld()` | Per-scenario cache cleared; scoped component reference dropped |
| All scenarios done | JVM shutdown | Root component and all singletons eligible for GC |

### Scenario isolation guarantee

Because a **new** `GeneratedScopedComponent` is created before every scenario:

- Any mutable state stored in a `@ScenarioScoped` object is **invisible** to other scenarios.
- The **same** `@ScenarioScoped` object instance is shared across all step definitions within the **same** scenario.
- Singletons in the root component **are shared** across scenarios.

---

## Sharing state across step definitions

A common Cucumber pattern is to share "scenario context" — mutable state accumulated across steps — between multiple step-definition classes.

With `dagger-cucumber`, this is handled naturally by `@ScenarioScoped`:

```java
// Shared scenario state
@ScenarioScoped
public final class ScenarioContext {
    private Response lastResponse;

    @Inject public ScenarioContext() {}

    public void setResponse(Response r) { lastResponse = r; }
    public Response lastResponse()      { return lastResponse; }
}

// Step class 1
public final class ApiSteps {
    private final ScenarioContext ctx;
    @Inject public ApiSteps(ScenarioContext ctx) { this.ctx = ctx; }

    @When("I call the API")
    public void callApi() { ctx.setResponse(api.call()); }
}

// Step class 2
public final class AssertionSteps {
    private final ScenarioContext ctx;
    @Inject public AssertionSteps(ScenarioContext ctx) { this.ctx = ctx; }

    @Then("the response status is {int}")
    public void checkStatus(int code) {
        assertThat(ctx.lastResponse().statusCode()).isEqualTo(code);
    }
}
```

Both step classes receive the **same** `ScenarioContext` instance within a scenario because Dagger's subcomponent caches `@ScenarioScoped` bindings for the subcomponent's lifetime.

---

## Hooks and `@ScenarioScoped` objects

Cucumber `@Before` and `@After` hooks work the same way as step definitions — inject via an `@Inject` constructor:

```java
public final class DatabaseHooks {

    private final TransactionManager txManager;

    @Inject
    public DatabaseHooks(TransactionManager txManager) {
        this.txManager = txManager;
    }

    @Before
    public void beginTransaction() {
        txManager.begin();
    }

    @After
    public void rollbackTransaction() {
        txManager.rollback();
    }
}
```

`TransactionManager` can be singleton (root) or scoped — Dagger resolves the correct instance automatically.

> **Note:** Hook classes need an `@Inject` constructor and must be in the glue package, just like step definition classes. They do not need `@ScenarioScoped` on the class itself unless you want the hook object's mutable state to be scoped.

---

## Limitations and known constraints

| Constraint | Detail |
|-----------|--------|
| Qualified `@ScenarioScoped` providers (Style B) | `@Named` or other qualifier annotations on Style-B provider methods are not supported; a compile error is emitted |
| Components requiring a builder | If `create()` is absent on the generated wrapper component (e.g. the root component uses `@BindsInstance`), the runtime will throw at startup — tracked in [#18](https://github.com/jossmoff/dagger-cucumber/issues/18) |
| One root component per test classpath | Exactly one `@CucumberDaggerConfiguration`-annotated interface is allowed; multiple produce a compile error |
| Glue-package restriction | Only `@ScenarioScoped` classes and step definition classes in the configured glue package are picked up by the processor |

---

## Further reading

- [Getting Started](getting-started.md) — installation and a minimal working example
- [Architecture](architecture.md) — how the runtime and processor work together
- [Configuration Reference](configuration-reference.md) — full API reference for `@ScenarioScoped` and other annotations
- [Dagger scopes guide](https://dagger.dev/dev-guide/scope) — background on how Dagger scopes work
- [Dagger subcomponents](https://dagger.dev/dev-guide/subcomponents) — how subcomponents are used to implement per-scenario scope
