plugins {
    `java-library`
    `maven-publish`
}

var apiVersion = "0.1-SNAPSHOT"

group = "gg.moonrise.quests"
version = apiVersion

dependencies {
    compileOnly(libs.paper)
}

val targetJavaVersion = 21
java {
    withSourcesJar()
    withJavadocJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.named<Jar>("jar") {
    enabled = true
}

tasks.named<Jar>("sourcesJar") {
    enabled = true
}

tasks.named<Jar>("javadocJar") {
    enabled = true
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifactId = "quests-sdk"

            pom {
                name.set("quests-sdk")
                description.set("QuestsPlus SDK")
                url.set("https://github.com/moonrise-studios/QuestsPlus")
            }
        }
    }

    repositories {
        maven {
            name = "nexus"

            val snapshotsUrl = findProperty("nexusSnapshotsUrl") as String?
                ?: "https://repo.moonrise.gg/repository/maven-snapshots"
            val releasesUrl = findProperty("nexusReleasesUrl") as String?
                ?: "https://repo.moonrise.gg/repository/maven-releases"
            val isRelease = findProperty("isRelease") == "true"
            val repositoryUrl = uri(if (isRelease) releasesUrl else snapshotsUrl)

            url = repositoryUrl

            if (repositoryUrl.scheme != "file") {
                credentials {
                    username = findProperty("nexusUsername") as String? ?: ""
                    password = findProperty("nexusPassword") as String? ?: ""
                }
            }
        }
    }
}
