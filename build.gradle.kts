plugins {
    id("java")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()

        maven("https://repo.og-network.net/repository/maven-releases/")
        maven("https://repo.og-network.net/repository/maven-snapshots/")

        maven("https://repo.moonrise.gg/repository/maven-releases/")
        maven("https://repo.moonrise.gg/repository/maven-snapshots/")
        maven("https://repo.moonrise.gg/repository/experimental/")

        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://repo.rosewooddev.io/repository/public/")
    }

    configurations.named("testCompileOnly") {
        extendsFrom(configurations.named("compileOnly").get())
    }
    configurations.named("testRuntimeOnly") {
        extendsFrom(configurations.named("compileOnly").get())
    }
    configurations.named("testAnnotationProcessor") {
        extendsFrom(configurations.named("annotationProcessor").get())
    }

    dependencies {
        add("testImplementation", libsCatalog.findLibrary("junitJupiter").get())
        add("testImplementation", libsCatalog.findLibrary("mockito").get())
        add("testRuntimeOnly", libsCatalog.findLibrary("junitPlatformLauncher").get())
    }

    plugins.withId("net.minecrell.plugin-yml.paper") {
        dependencies {
            add("testImplementation", libsCatalog.findLibrary("mockbukkit").get())
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.withType<Jar>() {
    enabled = false
}
