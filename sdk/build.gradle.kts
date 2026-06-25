plugins {
    `java-library`
}

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
