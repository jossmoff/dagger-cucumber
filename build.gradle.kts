import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    jacoco
    `maven-publish`
    signing

    id("com.palantir.git-version") version "5.0.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.palantir.baseline-error-prone") version "0.9.0"
}

group = "dev.joss"
val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()

repositories {
    mavenCentral()
}

dependencies {

    api("io.cucumber:cucumber-core:7.34.3")
    api("com.google.dagger:dagger:2.51")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
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
            artifactId = "dagger-cucumber"
            from(components["java"])

            pom {
                name.set("dagger-cucumber")
                description.set("A dagger cucumber extension")
                url.set("https://github.com/jossmoff/dagger-cucumber")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("The MIT License (MIT)")
                        url.set("http://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("jossmoff")
                        name.set("Joss Moffatt")
                        email.set("josshmoffatt@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git@github.com:jossmoff/dagger-cucumber.git")
                    developerConnection.set("scm:git@github.com:jossmoff/dagger-cucumber.git")
                    url.set("https://github.com/jossmoff/dagger-cucumber")
                }
            }
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            // gradle-nexus-publish-plugin expects String? here; cast from Any?
            username.set(findProperty("sonatypeUsername") as String?)
            password.set(findProperty("sonatypePassword") as String?)
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String?
    val signingPassword = findProperty("signingPassword") as String?
    useInMemoryPgpKeys(signingKey, signingPassword)

    sign(publishing.publications["mavenJava"])
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.disable("PreferSafeLoggableExceptions", "PreferSafeLoggingPreconditions", "PreferSafeLogger", "Slf4jLogsafeArgs")
}
