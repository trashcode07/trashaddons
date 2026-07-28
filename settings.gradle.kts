pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }

    val loom_version: String by settings
    plugins {
        id("com.github.johnrengelman.shadow") version "8.1.1"
        id("net.fabricmc.fabric-loom") version loom_version
    }
}

rootProject.name = providers.gradleProperty("mod_name").get()
