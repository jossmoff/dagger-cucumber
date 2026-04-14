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
    implementation(libs.javapoet)
    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(project(":cucumber-dagger"))
    testImplementation(libs.compile.testing)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testAnnotationProcessor(libs.auto.service)
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
