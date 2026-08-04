plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
    java
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("base_group").get()

base {
    archivesName = providers.gradleProperty("mod_name").get()
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.ccbluex.net/snapshots")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")

    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("com.github.CobaltScripts:Cobalt:26.2-1.0.6.3cd5419")
}

tasks {
    processResources {
        val modId = providers.gradleProperty("mod_id").get()

        filesMatching(listOf("cobalt.addon.json", "fabric.mod.json", "$modId.mixins.json")) {
            expand(getProperties())
        }
    }

    jar {
        exclude("fabric.mod.json")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
