import kotlin.system.exitProcess

pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "EngineHub Repository"
            url = uri("https://maven.enginehub.org/repo/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.17.17"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // Loom needs its project-local Minecraft repos; EngineHub is declared on Loom modules.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "EngineHub Repository"
            url = uri("https://maven.enginehub.org/repo/")
        }
        maven {
            name = "EngineHub repo.enginehub.org"
            url = uri("https://repo.enginehub.org/libs-release/")
        }
        maven {
            name = "IntellectualSites"
            url = uri("https://repo.intellectualsites.dev/repository/maven-all/")
        }
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
        }
        mavenCentral()
        maven {
            name = "Minecraft Libraries"
            url = uri("https://libraries.minecraft.net/")
        }
    }
}

if (!File("$rootDir/.git").exists()) {
    logger.lifecycle("""
    **************************************************************************************
    You need to fork and clone this repository! Don't download a .zip file.
    **************************************************************************************
    """.trimIndent()
    ).also { exitProcess(1) }
}

logger.lifecycle("""
*******************************************
 You are building FastAsyncWorldEdit (Fabric)!

 Target: Minecraft 26.2 / Fabric / Java 25
 Output: worldedit-fabric/build/libs
*******************************************
""")

rootProject.name = "FastAsyncWorldEdit"

includeBuild("build-logic")

include("worldedit-libs")
include("worldedit-libs:core")
include("worldedit-libs:core:ap")
include("worldedit-libs:core-mc")
include("worldedit-libs:fabric")

include("worldedit-core")
include("worldedit-core-mc")
include("worldedit-fabric")
include("worldedit-fabric:adapters:adapter-26.2")
