import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription
import org.gradle.kotlin.dsl.configure

plugins {
    id("java")
    alias(libs.plugins.shadow)
    alias(libs.plugins.paperPluginYml)
    alias(libs.plugins.modrinthMinotaur)
}

val identifier = "QuestsPlus"
val location = "gg.moonrise.quests"
val pluginVersion = "0.1"

group = "gg.moonrise"
version = pluginVersion

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
    compileOnly(libs.mariadb)
    compileOnly(libs.mysql)
    compileOnly(libs.postgresql)
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

val modrinthGameVersions = providers.environmentVariable("MODRINTH_GAME_VERSIONS")
    .map { versions -> versions.split(",").map(String::trim).filter(String::isNotEmpty) }
    .orElse(listOf("1.21.8"))
val modrinthLoaders = providers.environmentVariable("MODRINTH_LOADERS")
    .map { loaders -> loaders.split(",").map(String::trim).filter(String::isNotEmpty) }
    .orElse(listOf("paper"))

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(providers.environmentVariable("MODRINTH_PROJECT_ID").orElse("questsplus"))
    versionNumber.set(providers.environmentVariable("MODRINTH_VERSION_NUMBER").orElse(pluginVersion))
    versionName.set(providers.environmentVariable("MODRINTH_VERSION_NAME").orElse("$identifier $pluginVersion"))
    versionType.set(providers.environmentVariable("MODRINTH_VERSION_TYPE").orElse("release"))
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(modrinthGameVersions)
    loaders.addAll(modrinthLoaders)
    changelog.set(providers.environmentVariable("MODRINTH_CHANGELOG").orElse("Automated release."))
}

tasks.named("modrinth") {
    dependsOn(tasks.shadowJar)
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
            required = false
        }
        register("Vault") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
    }
}
