# dagger-cucumber

`dagger-cucumber` integrates [Dagger 2](https://dagger.dev) dependency injection with [Cucumber JVM](https://cucumber.io/docs/installation/java/), providing compile-time-safe, per-scenario object graphs for your Cucumber test suite.

## Features

- **Compile-time safety** — the annotation processor validates your configuration and generates all Dagger wiring at build time; no runtime classpath scanning
- **Per-scenario isolation** — objects annotated with `@ScenarioScoped` are created fresh for each scenario and discarded when it ends
- **Singleton sharing** — `@Singleton` services live in the root component and are shared across all scenarios
- **Two scoping styles** — annotate the class directly (Style A) or use a `@Provides` method in a module (Style B)
- **MethodHandle-based dispatch** — provision methods are pre-computed once at startup for low-overhead `getInstance()` calls

## Quick start

```kotlin
// build.gradle.kts
dependencies {
    testImplementation(platform("dev.joss:cucumber-dagger-bom:<version>"))
    testImplementation("dev.joss:cucumber-dagger")
    testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
    testAnnotationProcessor("com.google.dagger:dagger-compiler:2.59.2")
}
```

```java
// Annotate your root Dagger component
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {ServiceModule.class})
public interface AppComponent {}

// Annotate scenario-scoped state
@ScenarioScoped
public final class ScenarioState {
    @Inject public ScenarioState() {}
}

// Inject into step definitions
public final class MySteps {
    private final ScenarioState state;
    @Inject public MySteps(ScenarioState state) { this.state = state; }
}
```

```properties
# src/test/resources/junit-platform.properties
cucumber.glue=com.example
```

## Documentation

| Guide | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Installation (Gradle & Maven), minimal working example, compile-time output |
| [Scenario Scopes](docs/scenario-scopes.md) | `@ScenarioScoped` lifecycle, Style A vs Style B, isolation guarantees, sharing state |
| [Architecture](docs/architecture.md) | Runtime SPI bridge, annotation-processor pipeline, all generated files |
| [Configuration Reference](docs/configuration-reference.md) | Full API reference for all annotations and interfaces |
| [Troubleshooting](docs/troubleshooting.md) | Common error messages and remedies |

## Modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `cucumber-dagger` | `dev.joss:cucumber-dagger` | Runtime — Cucumber SPI implementations (`DaggerObjectFactory`, `DaggerBackend`) |
| `cucumber-dagger-processor` | `dev.joss:cucumber-dagger-processor` | Annotation processor — generates the Dagger wiring at compile time |
| `cucumber-dagger-bom` | `dev.joss:cucumber-dagger-bom` | Bill of Materials — pins both artifacts to a single version |

## License

[Apache 2.0](LICENSE)
