plugins {
    id("net.fabricmc.fabric-loom")
    `java-library`
    id("buildlogic.common-java")
}

repositories {
    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "EngineHub libs"
        url = uri("https://repo.enginehub.org/libs-release/")
    }
}

dependencies {
    compileOnly(project(":worldedit-core"))
    compileOnly(project(":worldedit-core-mc"))
    minecraft(libs.fabric.minecraft)
    compileOnly(libs.fabric.loader)
    compileOnly(libs.errorprone.annotations)
}
