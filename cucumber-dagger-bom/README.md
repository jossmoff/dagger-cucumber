# cucumber-dagger-bom

The Bill of Materials (BOM) for dagger-cucumber.

## What a BOM is

A BOM is a special POM artifact that declares a consistent set of dependency versions, allowing you to import it once and then omit version numbers on individual dependencies.

## Why use it

Without the BOM you must keep the versions of `cucumber-dagger` and `cucumber-dagger-processor` in sync manually. Importing the BOM ensures both artifacts - and any future `dev.joss:cucumber-dagger*` artifacts - always use the same tested version.

## How to use it

Import the BOM as a platform dependency (Gradle) or in `<dependencyManagement>` (Maven), then declare the individual `dev.joss` dependencies without versions.

**Gradle Kotlin DSL:**
```kotlin
dependencies {
    testImplementation(platform("dev.joss:cucumber-dagger-bom:<version>"))
    testImplementation("dev.joss:cucumber-dagger")
    testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
    testAnnotationProcessor("com.google.dagger:dagger-compiler:<dagger-version>")
}
```

**Gradle Groovy DSL:**
```groovy
dependencies {
    testImplementation platform("dev.joss:cucumber-dagger-bom:<version>")
    testImplementation "dev.joss:cucumber-dagger"
    testAnnotationProcessor "dev.joss:cucumber-dagger-processor"
    testAnnotationProcessor "com.google.dagger:dagger-compiler:<dagger-version>"
}
```

**Maven:**
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
```

> **Maven note:** `annotationProcessorPaths` in `maven-compiler-plugin` is resolved outside the BOM — you must declare `cucumber-dagger-processor` and `dagger-compiler` versions explicitly there. See [getting-started.md](../docs/getting-started.md) for the full `maven-compiler-plugin` configuration.

Only `dev.joss` artifact versions are managed by this BOM. You still need to specify the version for `com.google.dagger:dagger-compiler` separately.

## Further reading

- [Root README](../README.md) - full setup instructions including the complete dependency block
