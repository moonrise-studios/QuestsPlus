import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription
import org.gradle.kotlin.dsl.configure

plugins {
    id("java")
    alias(libs.plugins.shadow)
    alias(libs.plugins.paperPluginYml)
}

val identifier = "QuestsPlus"
val location = "gg.moonrise.quests"
val pluginVersion = "0.1"

repositories {
    mavenCentral()
    maven("https://repo.rosewooddev.io/repository/public/")
}

dependencies {
    implementation(project(":sdk"))
    implementation(libs.engine)

    // Spring & Jakarta
    compileOnly(libs.springContext)
    compileOnly(libs.jakarta)

    // Paper
    compileOnly(libs.paper)

    // Cloud Command Framework
    compileOnly(libs.cloudAnnotations)
    compileOnly(libs.cloudPaper)

    // Moss
    compileOnly(libs.mossCommon)
    compileOnly(libs.mossPaper)

    // Config
    compileOnly(libs.configLib)

    // Persistence and cache
    compileOnly(libs.hikari)
    compileOnly(libs.sqlite)
    compileOnly(libs.cache)
    compileOnly(libs.gson)
    compileOnly(libs.roseStacker)
    compileOnly(libs.playerPoints)
    compileOnly(libs.vault) {
        isTransitive = false
    }

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

val targetJavaVersion = 21
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.shadowJar {
    archiveBaseName.set(identifier)
    archiveClassifier.set("")
    archiveVersion.set("")

    relocate("gg.moonrise.engine", "$location.libs.engine")

    destinationDirectory.set(rootProject.file("build/libs"))
}

configure<PaperPluginDescription> {
    name = identifier
    apiVersion = "1.21"
    version = pluginVersion
    main = "$location.QuestsPlusPlugin"
    loader = "$location.loader.QuestsPlusPluginLoader"
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD

    serverDependencies {
        register("RoseStacker") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
        register("PlayerPoints") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = true
        }
        register("Vault") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = true
        }
    }
}
