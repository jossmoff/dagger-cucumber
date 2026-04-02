import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("com.diffplug.spotless") version "8.4.0"
    id("com.palantir.baseline-error-prone") version "6.79.0"
}

dependencies {
    testImplementation(project(":cucumber-dagger"))
    testAnnotationProcessor(project(":cucumber-dagger-processor"))
    testAnnotationProcessor("com.google.dagger:dagger-compiler:2.59.2")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.platform:junit-platform-suite-api:1.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-suite-engine:1.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.34.3")
    testImplementation("io.cucumber:cucumber-java:7.34.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

spotless {
    java {
        googleJavaFormat()
    }
}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-platform-suite")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.disable(
        "PreferSafeLoggableExceptions",
        "PreferSafeLoggingPreconditions",
        "PreferSafeLogger",
        "Slf4jLogsafeArgs"
    )
}
