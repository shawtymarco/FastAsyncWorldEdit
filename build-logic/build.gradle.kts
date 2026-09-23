plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "EngineHub Repository"
        url = uri("https://maven.enginehub.org/repo/")
    }
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(libs.grgit)
    implementation(libs.shadow)
    implementation(libs.paperweight)
    // Apply Loom via pluginManagement / settings; keep classpath hint for version alignment
    compileOnly(libs.fabric.loom)

    constraints {
        val asmVersion = "[${libs.versions.minimumAsm.get()},)"
        implementation("org.ow2.asm:asm:$asmVersion") {
            because("Need Java 21 support in shadow")
        }
        implementation("org.ow2.asm:asm-commons:$asmVersion") {
            because("Need Java 21 support in shadow")
        }
        implementation("org.vafer:jdependency:[${libs.versions.minimumJdependency.get()},)") {
            because("Need Java 21 support in shadow")
        }
    }
}
