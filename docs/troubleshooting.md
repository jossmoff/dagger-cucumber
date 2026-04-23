# Troubleshooting

## Quick reference

| Error | Likely cause |
|---|---|
| `Type X is not provided by any component` | Missing `@Inject` constructor or wrong package |
| `GeneratedComponentResolver not found` | Processor not configured or `@CucumberDaggerConfiguration` missing |
| `Only one @CucumberDaggerConfiguration is allowed` | Two interfaces annotated |
| `@CucumberDaggerConfiguration can only be applied to interfaces` | Annotation on a class |
| `Qualified @ScenarioScope provider methods are not currently supported` | `@ScenarioScope` + `@Named` on same method |
| `has neither a static create() nor a static builder() method` | `dagger-compiler` not configured so Dagger did not generate a component implementation |
| `Multiple CucumberDaggerComponent factories found` | Two subprojects merged onto one classpath |

---

## "Type X is not provided by any component"

```
java.lang.IllegalStateException: Type com.example.tests.CheckoutSteps is not provided by any component.
```

**Cause.** The generated resolver has no dispatch entry for the type. This happens when:
- The class has no `@Inject`-annotated constructor.
- The class is outside the glue package tree (package doesn't start with the root component's package).
- The type is not a step definition and has no `@Provides` binding.

**Fix.**
1. Add `@Inject` to the constructor.
2. Move the class into the root component's package or a sub-package.
3. For non-step-definition types, add a `@Provides` method in a listed module.

---

## "GeneratedComponentResolver not found"

```
java.lang.IllegalStateException: GeneratedComponentResolver not found.
```

**Cause.** `dev.joss.dagger.cucumber.generated.GeneratedComponentResolver` does not exist on the classpath. The processor generates this class.

**Fix.**
1. Confirm `cucumber-dagger-processor` is an `annotationProcessor` dependency:
   ```kotlin
   testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
   ```
2. Confirm exactly one interface carries `@CucumberDaggerConfiguration`.
3. Run `./gradlew clean test` to force the processor to re-run.

---

## "Only one @CucumberDaggerConfiguration is allowed"

```
error: Only one @CucumberDaggerConfiguration is allowed
```

**Fix.** Remove `@CucumberDaggerConfiguration` from all but one interface. Separate test suites should live in separate Gradle subprojects.

---

## "@CucumberDaggerConfiguration can only be applied to interfaces"

```
error: @CucumberDaggerConfiguration can only be applied to interfaces
```

**Fix.** Convert the annotated type to an interface:

```java
// Incorrect
@CucumberDaggerConfiguration
public class IntegrationTestConfig {}

// Correct
@CucumberDaggerConfiguration
public interface IntegrationTestConfig {}
```

---

## "Qualified @ScenarioScope provider methods are not currently supported"

```
error: Qualified @ScenarioScope provider methods are not currently supported
```

**Cause.** A `@Provides` method combines `@ScenarioScope` with a qualifier like `@Named`.

**Fix.** Remove the qualifier. To distinguish multiple bindings of the same type, introduce a dedicated wrapper type:

```java
public final class PrimaryBasket {
    public final Basket basket;
    public PrimaryBasket(Basket basket) { this.basket = basket; }
}

@Provides
@ScenarioScope
static PrimaryBasket providePrimaryBasket(ApiClient client) {
    return new PrimaryBasket(new Basket(client));
}
```

---

## "has neither a static create() nor a static builder() method"

```
java.lang.IllegalStateException: DaggerGeneratedCucumberIntegrationTestConfig
 has neither a static create() nor a static builder() method.
```

**Cause.** The Dagger-generated component class was found on the classpath (otherwise you would see `Could not load component class`), but it has no `static create()` or `static builder()` method. This happens when `dagger-compiler` is not configured as an annotation processor, so Dagger never generated a `DaggerGeneratedCucumber*` implementation — only the interface compiled by `javac` is present.

**Fix.**
1. Confirm both annotation processors are declared:
   ```kotlin
   testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
   testAnnotationProcessor("com.google.dagger:dagger-compiler:<version>")
   ```
2. Run `./gradlew clean test`.

---

## "Multiple CucumberDaggerComponent factories found"

```
java.lang.IllegalStateException: Multiple CucumberDaggerComponent factories found: [...]
```

**Cause.** Two service file entries exist — typically from two subprojects merged onto the same classpath.

**Fix.** Each test suite should have its own Gradle subproject. Only one subproject should run the cucumber-dagger processor.

---

## Enable Dagger compiler errors

If your build fails at runtime with `ClassNotFoundException` or `NoSuchMethodException`, confirm both processors are present:

```kotlin
testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
testAnnotationProcessor("com.google.dagger:dagger-compiler:<version>")
```

Missing `dagger-compiler` turns compile-time graph errors into runtime failures. To see processor output:

```
./gradlew test --info 2>&1 | grep -i "dagger\|cucumber\|error"
```
