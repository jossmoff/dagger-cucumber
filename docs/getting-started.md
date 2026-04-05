# Getting Started

This guide walks you through adding `dagger-cucumber` to an existing Cucumber test suite and writing your first scenario-scoped injection.

## Prerequisites

- Java 21+
- [Dagger 2](https://dagger.dev) already familiar — you will write a `@Component` interface
- [Cucumber for Java](https://cucumber.io/docs/installation/java/) set up with the JUnit Platform engine

## Installation

### Gradle (recommended)

Use the BOM to keep both artifacts in sync:

```kotlin
// build.gradle.kts
dependencies {
    // BOM — pins both artifacts to a single version
    testImplementation(platform("dev.joss:cucumber-dagger-bom:<version>"))

    // Runtime — Cucumber SPI implementations
    testImplementation("dev.joss:cucumber-dagger")

    // Annotation processor — generates the Dagger wiring at compile time
    testAnnotationProcessor("dev.joss:cucumber-dagger-processor")

    // Dagger compiler must also run in the test annotation-processor slot
    testAnnotationProcessor("com.google.dagger:dagger-compiler:2.59.2")
}
```

### Maven

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.joss</groupId>
      <artifactId>cucumber-dagger-bom</artifactId>
      <version>${cucumber-dagger.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>dev.joss</groupId>
    <artifactId>cucumber-dagger</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
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
      </configuration>
    </plugin>
  </plugins>
</build>
```

## Minimal working example

The following shows all the moving parts of a complete, working setup.

### 1. Define your root component

Create a Dagger `@Component` interface and annotate it with `@CucumberDaggerConfiguration`. This is the only annotation you need from `dagger-cucumber`'s perspective on the component itself — the processor injects everything else automatically.

```java
// src/test/java/com/example/AppComponent.java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {ServiceModule.class})
public interface AppComponent {}
```

- **One** `@CucumberDaggerConfiguration`-annotated interface must exist in your test classpath.
- List only _your_ application modules in `@Component(modules = …)`. The processor adds its own generated module automatically.

### 2. Provide singleton services

Singletons are ordinary Dagger bindings scoped with `@Singleton`. They are created once and shared across all scenarios.

```java
// src/test/java/com/example/ServiceModule.java
@Module
public final class ServiceModule {

    @Provides
    @Singleton
    static MyService provideMyService() {
        return new MyService();
    }
}
```

### 3. Declare scenario-scoped objects (Style A)

Annotate a class with `@ScenarioScoped` and give it an `@Inject` constructor. A fresh instance is created at the start of each scenario and discarded at the end.

```java
// src/test/java/com/example/ScenarioState.java
@ScenarioScoped
public final class ScenarioState {

    private final MyService service;
    private String result;

    @Inject
    public ScenarioState(MyService service) {
        this.service = service;
    }

    public void runAction(String input) {
        result = service.process(input);
    }

    public String result() { return result; }
}
```

### 4. Write step definitions

Step definition classes need an `@Inject` constructor. The framework injects both singleton and scenario-scoped dependencies.

```java
// src/test/java/com/example/MySteps.java
public final class MySteps {

    private final ScenarioState state;

    @Inject
    public MySteps(ScenarioState state) {
        this.state = state;
    }

    @When("I process {string}")
    public void iProcess(String input) {
        state.runAction(input);
    }

    @Then("the result is {string}")
    public void theResultIs(String expected) {
        assertThat(state.result()).isEqualTo(expected);
    }
}
```

### 5. Configure Cucumber

`junit-platform.properties` (in `src/test/resources`) tells Cucumber where to find your step definitions and feature files:

```properties
cucumber.glue=com.example
cucumber.publish.quiet=true
```

### 6. Add the test runner

```java
// src/test/java/com/example/CucumberRunnerTest.java
@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.example")
public class CucumberRunnerTest {}
```

### 7. Write features

```gherkin
# src/test/resources/com/example/my.feature
Feature: Example

  Scenario: Processing works
    When I process "hello"
    Then the result is "HELLO"
```

### 8. Run

```bash
./gradlew test
# or
mvn test
```

## What happens at compile time

When you build, the annotation processor runs and generates the following files in the same package as your `AppComponent`:

| Generated file | Purpose |
|----------------|---------|
| `GeneratedScopedComponent` | Per-scenario Dagger `@Subcomponent` with a provision method for every scoped type and step definition |
| `GeneratedScopedModule` | `@Module` for the subcomponent |
| `CucumberDaggerModule` | Binds the subcomponent builder into the root component |
| `GeneratedCucumberAppComponent` | Wrapper `@Component` that combines your modules with `CucumberDaggerModule` |
| `ScenarioScopedComponentAccessor` | Fast-path class that returns the generated subcomponent type without a classpath scan |
| `META-INF/services/…CucumberDaggerComponent` | Service-loader entry used by the runtime to locate the root component factory |

See [Architecture](architecture.md) for a detailed breakdown of each file and [Scenario Scopes](scenario-scopes.md) for the full scoping model.

## Next steps

- [Architecture](architecture.md) — understand the runtime SPI bridge and what the processor generates
- [Scenario Scopes](scenario-scopes.md) — Style A vs Style B, lifecycle, and isolation guarantees
- [Configuration Reference](configuration-reference.md) — full API reference for all annotations and interfaces
- [Troubleshooting](troubleshooting.md) — common error messages and how to fix them
