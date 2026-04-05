# cucumber-dagger-processor

Annotation processor for `dagger-cucumber`. Runs at compile time to inspect your `@CucumberDaggerConfiguration` component and generate the Dagger subcomponent, module, and service-loader wiring needed by the runtime.

## What this module generates

Given a root component annotated with `@CucumberDaggerConfiguration`, the processor generates six files in the same package:

| Generated file | Purpose |
|----------------|---------|
| `GeneratedScopedComponent` | Per-scenario `@Subcomponent` with a provision method for every `@ScenarioScoped` type and step definition |
| `GeneratedScopedModule` | `@Module` for the subcomponent; includes Style-B user modules |
| `CucumberDaggerModule` | Declares the subcomponent; provides `ScenarioScopedComponent.Builder` into the root graph |
| `GeneratedCucumberAppComponent` | Wrapper `@Component` combining your modules with `CucumberDaggerModule` |
| `ScenarioScopedComponentAccessor` | Returns `GeneratedScopedComponent.class`; used by the runtime as a fast-path lookup |
| `META-INF/services/…CucumberDaggerComponent` | ServiceLoader entry pointing to `DaggerGeneratedCucumberAppComponent` |

## Processor pipeline

The processor runs a four-step immutable pipeline triggered by `@CucumberDaggerConfiguration`:

1. **`FindRootComponentStep`** — validates exactly one `@CucumberDaggerConfiguration` interface exists
2. **`CollectScopedClassesStep`** — collects `@ScenarioScoped` classes in the glue package; validates concrete class + `@Inject` constructor requirements
3. **`CollectStepDefsStep`** — collects step definition classes (those with `@Inject` constructors, not `@ScenarioScoped`) in the glue package
4. **`BuildProcessingModelStep`** — assembles Style-A and Style-B provision methods; rejects qualified Style-B providers

The pipeline short-circuits on the first step that reports a compile error.

## Dependency

Configure as a test annotation processor — **not** as a regular compile or runtime dependency:

```kotlin
// Gradle
testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
// Dagger's own compiler must also run in the test annotation-processor slot
testAnnotationProcessor("com.google.dagger:dagger-compiler:2.59.2")
```

```xml
<!-- Maven -->
<annotationProcessorPaths>
  <path>
    <groupId>dev.joss</groupId>
    <artifactId>cucumber-dagger-processor</artifactId>
    <version>${cucumber-dagger.version}</version>
  </path>
  <path>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger-compiler</artifactId>
    <version>2.59.2</version>
  </path>
</annotationProcessorPaths>
```

## Further reading

- [Architecture](../docs/architecture.md) — detailed processor pipeline diagram and generated-file breakdown
- [Configuration Reference](../docs/configuration-reference.md) — all compile-time validation rules and error messages
- [Troubleshooting](../docs/troubleshooting.md) — how to fix processor errors
