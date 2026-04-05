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

    testImplementation(project(":cucumber-dagger"))
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("com.google.auto.service:auto-service:1.1.1")
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
        "Slf4jLogsafeArgs",
        "StringConcatToTextBlock"
    )
}
