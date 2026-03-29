plugins {
    `java-platform`
    `maven-publish`
    signing
}

dependencies {
    constraints {
        api(project(":cucumber-dagger"))
        api(project(":cucumber-dagger-processor"))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            groupId = "dev.joss"
            artifactId = "cucumber-dagger-bom"
            from(components["javaPlatform"])
            pom {
                name.set("cucumber-dagger-bom")
                description.set("Bill of Materials for dagger-cucumber")
            }
        }
    }
}
