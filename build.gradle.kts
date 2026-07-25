plugins {
    alias(libs.plugins.git.version)
    alias(libs.plugins.nexus.publish)
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

    plugins.withId("com.palantir.baseline-error-prone") {
        // TODO: Remove this once baseline-error-prone is compatible with error_prone_core 2.47+.
        // Dagger 2.60.1 pulls in Guava 33.6.0-jre, whose metadata aligns Error Prone artifacts to
        // 2.47.0. baseline-error-prone 6.79.0 still references AndroidJdkLibsChecker, which was
        // removed there, so keep the pin narrowly scoped to the affected configurations.
        val errorProneVersion = libs.versions.error.prone.get()
        configurations.matching {
            it.name == "testAnnotationProcessor" || it.name == "errorprone"
        }.configureEach {
            resolutionStrategy.force(
                "com.google.errorprone:error_prone_core:$errorProneVersion",
                "com.google.errorprone:error_prone_annotation:$errorProneVersion",
                "com.google.errorprone:error_prone_check_api:$errorProneVersion"
            )
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
