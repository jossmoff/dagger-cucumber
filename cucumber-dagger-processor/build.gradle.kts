import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    jacoco
    `maven-publish`
    signing
    id("com.diffplug.spotless") version "8.4.0"
    id("com.palantir.baseline-error-prone") version "6.79.0"
}

dependencies {
    implementation("com.squareup:javapoet:1.13.0")
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "dev.joss"
            artifactId = "cucumber-dagger-processor"
            from(components["java"])
            pom {
                name.set("cucumber-dagger-processor")
                description.set("Annotation processor for dagger-cucumber")
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
