import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.json.JsonSlurper

plugins {
    id("net.fabricmc.fabric-loom")
    `java-library`
    id("buildlogic.platform")
}

platform {
    kind = buildlogic.WorldEditKind.Mod
    includeClasspath = true
}

val fabricApiConfiguration: Configuration = configurations.create("fabricApi")

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
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
    api(project(":worldedit-core-mc"))
    implementation(project(":worldedit-fabric:adapters:adapter-26.2"))

    // Runtime libs that core treats as compileOnly — must ship in the Fabric dist jar
    // (same set Bukkit shades into FAWE-Paper).
    api(libs.parallelgzip) { isTransitive = false }
    api(libs.lz4Java) { isTransitive = false }
    api(libs.sparsebitset) { isTransitive = false }
    // SnakeYAML is used reflectively by config loading; must be shaded explicitly.
    api(libs.snakeyaml) { isTransitive = false }

    minecraft(libs.fabric.minecraft)
    implementation(libs.fabric.loader)
    include(libs.cuiProtocol.fabric)
    implementation(libs.cuiProtocol.fabric)
    // Fabric CUI jar does not declare its common dependency; pull it in explicitly.
    implementation(libs.cuiProtocol.common)
    include(libs.cuiProtocol.common)

    @Suppress("UNCHECKED_CAST")
    val fabricModJson = file("src/main/resources/fabric.mod.json").bufferedReader().use {
        JsonSlurper().parse(it) as Map<String, Map<String, *>>
    }
    val wantedDependencies = (fabricModJson["depends"] ?: error("no depends in fabric.mod.json")).keys
        .filter { it == "fabric-api-base" || it.contains(Regex("v\\d$")) }
        .toSet()
    for (wantedDependency in wantedDependencies) {
        val dep = fabricApi.module(wantedDependency, libs.versions.fabric.api.get())
        include(dep)
        implementation(dep)
    }

    compileOnly(libs.fabric.permissions.api)
    compileOnly(libs.errorprone.annotations)
}

configure<BasePluginExtension> {
    archivesName.set("FastAsyncWorldEdit-Fabric-mc${libs.versions.fabric.minecraft.get()}")
}

configure<PublishingExtension> {
    publications.named<MavenPublication>("maven") {
        artifactId = the<BasePluginExtension>().archivesName.get()
        from(components["java"])
    }
}

tasks.named<Copy>("processResources") {
    val internalVersion = project.ext["internalVersion"]
    inputs.property("version", internalVersion)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to internalVersion))
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("dist")
    dependsOn("jar")
    from(rootProject.file("NOTICE.txt")) { into("META-INF"); rename { "NOTICE-FAWE.txt" } }
    from(rootProject.file("FABRIC-PORT.md")) { into("META-INF") }
    from({
        zipTree(tasks.named<Jar>("jar").get().archiveFile).matching {
            include("META-INF/jars/**")
        }
    })
    dependencies {
        relocate("org.antlr.v4", "com.sk89q.worldedit.antlr4")
        relocate("net.royawesome.jlibnoise", "com.sk89q.worldedit.jlibnoise")
        include(dependency("org.antlr:antlr4-runtime"))
        include(dependency("com.sk89q.lib:jlibnoise"))

        // ZSTD cannot be relocated (native loader looks up original package).
        include(dependency(libs.zstd))

        include(dependency(libs.lz4Java))
        include(dependency(libs.snakeyaml))

        relocate("com.zaxxer", "com.fastasyncworldedit.core.math") {
            include(dependency(libs.sparsebitset))
        }
        relocate("org.anarres", "com.fastasyncworldedit.core.internal.io") {
            include(dependency(libs.parallelgzip))
        }
    }
    minimize {
        exclude(dependency(libs.lz4Java))
        exclude(dependency(libs.parallelgzip))
        exclude(dependency(libs.sparsebitset))
        exclude(dependency(libs.zstd))
        exclude(dependency(libs.snakeyaml))
    }
}

tasks.named("assemble").configure {
    dependsOn("shadowJar")
}
