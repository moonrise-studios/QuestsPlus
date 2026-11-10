pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets Gradle download the Java 21 toolchain on machines that only have a newer JDK (for example the Jenkins agent).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "QuestsPlus"

include("sdk")
include("plugin")
