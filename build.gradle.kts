plugins {
    id("java")
    id("java-library")
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.aincraft"
version = "1.0.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://nexus.neetgames.com/repository/maven-releases/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

plugins.withType<JavaPlugin> {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    implementation("com.google.inject:guice:7.0.0")
    compileOnly("org.jetbrains:annotations:24.1.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    implementation("com.zaxxer:HikariCP:7.0.1")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
}

tasks {

    build {
        dependsOn(shadowJar)
    }

    named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
        val toolchains = project.extensions.getByType<JavaToolchainService>()
        javaLauncher.set(
            toolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        )
        minecraftVersion("1.21.7")
    }
}