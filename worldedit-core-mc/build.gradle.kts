plugins {
    id("net.fabricmc.fabric-loom")
    `java-library`
    id("buildlogic.core-and-platform")
}

description = "Core-MC"

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
    api(project(":worldedit-core"))
    api(project(":worldedit-libs:core-mc"))

    minecraft(libs.fabric.minecraft)
    compileOnly(libs.fabric.mixin)

    implementation(libs.cuiProtocol.common)

    compileOnly(libs.errorprone.annotations)
}

configure<BasePluginExtension> {
    archivesName.set("worldedit-core-mc-mc${libs.versions.fabric.minecraft.get()}")
}

configure<PublishingExtension> {
    publications.named<MavenPublication>("maven") {
        artifactId = the<BasePluginExtension>().archivesName.get()
        from(components["java"])
    }
}
