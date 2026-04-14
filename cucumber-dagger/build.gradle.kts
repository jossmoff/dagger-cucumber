import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    jacoco
    `maven-publish`
    signing
    alias(libs.plugins.spotless)
    alias(libs.plugins.baseline.error.prone)
}

dependencies {
    api(libs.cucumber.core)
    api(libs.dagger)
    api(libs.jakarta.inject.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
}

java {
    withSourcesJar()
    withJavadocJar()
}

spotless {
    java {
        googleJavaFormat()
    }
}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "dev.joss"
            artifactId = "cucumber-dagger"
            from(components["java"])
            pom {
                name.set("cucumber-dagger")
                description.set("A dagger cucumber extension")
            }
        }
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
