# cucumber-dagger

The runtime module for dagger-cucumber.

## What this module contains

- **Cucumber SPI implementations** - `DaggerObjectFactory` and `DaggerBackend` integrate Dagger into Cucumber's object-creation and backend lifecycle.
- **API annotations** - `@CucumberDaggerConfiguration` marks your root Dagger component; `@ScenarioScope` is the scope annotation for per-scenario bindings.
- **Internal lifecycle management** - `DaggerObjectFactory` creates a fresh scenario-scoped subcomponent at the start of each scenario and tears it down at the end, ensuring clean state between scenarios.

## Adding the dependency

```kotlin
testImplementation("dev.joss:cucumber-dagger")
```

When using the BOM, the version is managed automatically. Without it, specify the version explicitly:

```kotlin
testImplementation("dev.joss:cucumber-dagger:<version>")
```

## Annotation processor requirement

This module requires `cucumber-dagger-processor` on the annotation processor classpath at compile time. The processor generates the Dagger subcomponent, binding modules, and `ComponentResolver` that the runtime depends on. Without it, the application will fail to start.

```kotlin
testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
testAnnotationProcessor("com.google.dagger:dagger-compiler:<dagger-version>")
```

## Further reading

- [Project README](../README.md) - full setup instructions and a minimal working example
- [Getting started](../docs/getting-started.md) - step-by-step guide for a new project
