plugins {
    id("com.palantir.git-version") version "5.0.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

val gitVersion: groovy.lang.Closure<String> by extra

allprojects {
    group = "dev.joss"
    version = gitVersion()

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("java-library") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }

    plugins.withId("maven-publish") {
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications.withType<MavenPublication> {
                    pom {
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
    }

    plugins.withId("signing") {
        afterEvaluate {
            extensions.configure<SigningExtension> {
                val signingKey = findProperty("signingKey") as String?
                val signingPassword = findProperty("signingPassword") as String?
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
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
