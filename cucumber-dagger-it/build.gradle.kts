import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    alias(libs.plugins.spotless)
    alias(libs.plugins.baseline.error.prone)
}

dependencies {
    testImplementation(project(":cucumber-dagger"))
    testAnnotationProcessor(project(":cucumber-dagger-processor"))
    testAnnotationProcessor(libs.dagger.compiler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.platform.suite.api)
    testRuntimeOnly(libs.junit.platform.suite.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.assertj.core)
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
