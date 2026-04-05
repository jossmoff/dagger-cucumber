# Troubleshooting

This page lists the error messages you may encounter from the annotation processor or the runtime, explains what causes them, and shows how to fix each one.

---

## Compile-time errors (annotation processor)

These errors appear during `javac` / Gradle / Maven build as standard compiler diagnostics.

---

### `Only one @CucumberDaggerConfiguration is allowed`

**Cause:** More than one interface in the test classpath carries `@CucumberDaggerConfiguration`.

**Fix:** Remove `@CucumberDaggerConfiguration` from all but one interface. The annotation must appear exactly once.

---

### `@CucumberDaggerConfiguration can only be applied to interfaces`

**Cause:** `@CucumberDaggerConfiguration` was placed on a class rather than an interface.

**Fix:** Change the annotated type to an interface:

```java
// Wrong — class
@CucumberDaggerConfiguration
public class AppComponent { ... }

// Correct — interface
@CucumberDaggerConfiguration
public interface AppComponent {}
```

---

### `@ScenarioScoped can only be applied to concrete classes or @Provides methods`

**Cause:** `@ScenarioScoped` was placed on an interface, abstract class, or enum.

**Fix:**

- Remove the annotation from the invalid target.
- If you need scenario-scoped behaviour for an interface or abstract type, use Style B: add a `@Provides @ScenarioScoped` method in a Dagger module that returns the concrete implementation.

```java
// Wrong — interface
@ScenarioScoped
public interface Cart { ... }

// Correct — @Provides method in a module (Style B)
@Module
public abstract class TestModule {
    @Provides
    @ScenarioScoped
    static Cart provideCart() { return new CartImpl(); }
}
```

---

### `@ScenarioScoped class Foo must declare an @Inject constructor so Dagger can create it in the generated scoped subcomponent`

**Cause:** A class annotated with `@ScenarioScoped` does not have a constructor annotated with `@Inject`.

**Fix:** Add an `@Inject`-annotated constructor. If the class has no dependencies, use a no-arg constructor:

```java
@ScenarioScoped
public final class ScenarioContext {

    @Inject
    public ScenarioContext() {}
}
```

---

### `Qualified @ScenarioScoped provider methods are not currently supported: methodName`

**Cause:** A Style-B `@Provides @ScenarioScoped` method also carries a qualifier annotation such as `@Named`.

**Fix:** Remove the qualifier annotation from the `@ScenarioScoped` provider method. Qualified scoped providers are not currently supported. If you need multiple instances of the same type scoped to a scenario, consider wrapping them in a holder class:

```java
// Wrong
@Provides
@ScenarioScoped
@Named("primary")
static HttpClient providePrimaryClient() { ... }

// Alternative — wrap in a holder
@ScenarioScoped
public final class HttpClients {
    public final HttpClient primary;
    public final HttpClient secondary;

    @Inject
    public HttpClients() {
        primary = HttpClient.newHttpClient();
        secondary = HttpClient.newHttpClient();
    }
}
```

---

## Runtime errors

These errors appear as exceptions thrown during test execution.

---

### `IllegalStateException: DaggerObjectFactory has not been registered. Ensure cucumber-dagger-processor ran at compile time and generated DaggerGeneratedCucumberAppComponent.`

**Cause:** The `DaggerBackend` could not find a registered `DaggerObjectFactory` at `loadGlue()` time. This usually means the ServiceLoader registration did not work or the runtime JAR is on the classpath without the processor having run.

**Fix checklist:**

1. Verify `cucumber-dagger` (runtime) is on the test compile/runtime classpath.
2. Verify `cucumber-dagger-processor` is configured as a **test annotation processor** (not a regular dependency):
   - Gradle: `testAnnotationProcessor("dev.joss:cucumber-dagger-processor")`
   - Maven: inside `<annotationProcessorPaths>` of `maven-compiler-plugin`
3. Verify Dagger's compiler is also configured as an annotation processor in the same slot.
4. Clean and rebuild (`./gradlew clean test` or `mvn clean test`) to ensure generated sources are present.

---

### `IllegalStateException: buildWorld() called before configure(). …cucumber-dagger-processor…`

**Cause:** `DaggerObjectFactory.buildWorld()` was called without a prior call to `configure()`. In normal operation this means the processor did not run and therefore the generated `ScenarioScopedComponentAccessor` or `GeneratedCucumberAppComponent` is missing from the classpath.

**Fix:** Same checklist as above — ensure the annotation processor is correctly configured and a clean build has been run.

---

### `IllegalStateException: No provision method found for class Foo`

**Cause:** `DaggerObjectFactory.getInstance(Foo.class)` was called but neither the scoped subcomponent nor the root component has a provision method for `Foo`. This typically happens when:

- `Foo` is a step definition class that is not in the configured glue package.
- `Foo` is annotated with `@ScenarioScoped` but is outside the glue package (it will be silently ignored by the processor).
- `Foo` is a class the processor does not know about (e.g. not discovered during compilation).

**Fix:**

1. Confirm `cucumber.glue` in `junit-platform.properties` matches the package containing your step definition and `@ScenarioScoped` classes.
2. If the class is in the correct package, clean and rebuild to force the processor to regenerate the scoped component.

---

### `IllegalStateException: Root component does not have a create() factory method. …`

**Cause:** The processor generates `DaggerGeneratedCucumberAppComponent.create()` as the entry point. If the underlying root component requires a `@Component.Builder` with `@BindsInstance` parameters (or similar), Dagger's generated class will not have a no-arg `create()` method, and the runtime will fail at startup.

**Status:** Known limitation — tracked in [#18](https://github.com/jossmoff/dagger-cucumber/issues/18).

**Workaround:** Avoid `@BindsInstance` on the root component for now. Provide values via `@Module` factories or system properties instead.

---

### No tests are discovered / all scenarios are skipped

**Cause:** Cucumber cannot find the step definitions or the Dagger wiring was not applied at all.

**Fix checklist:**

1. Confirm your test runner uses `@IncludeEngines("cucumber")`.
2. Confirm `cucumber.glue` points to the correct package.
3. Confirm at least one `@CucumberDaggerConfiguration`-annotated component exists in the test classpath.
4. Run a clean build and check the annotation processor output (look for generated sources under `build/generated/sources/annotationProcessor/` or `target/generated-sources/`).

---

### Generated sources are not visible in the IDE

**Cause:** The IDE may not pick up annotation-processor outputs automatically.

**Fix:**

- **IntelliJ IDEA:** Enable annotation processing under *Settings → Build, Execution, Deployment → Compiler → Annotation Processors* and reimport the Gradle/Maven project.
- **Eclipse:** Ensure APT (Annotation Processing Tool) is enabled in project properties.
- After enabling, trigger a project rebuild.

---

## Further reading

- [Getting Started](getting-started.md) — installation and a minimal working example
- [Configuration Reference](configuration-reference.md) — full API reference including all compile-time error messages
- [Architecture](architecture.md) — how the runtime and processor work together
