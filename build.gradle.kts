plugins {
    id("buildlogic.common")
}

val rootVersion: String = "2.15.4"
val snapshot: String = "SNAPSHOT"
val revision: String = "-fabric"
val buildNumber: String = snapshot
val date: String = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yy.MM.dd"))

extra.set("rootVersion", rootVersion)
extra.set("snapshot", snapshot)
extra.set("revision", revision)
extra.set("buildNumber", buildNumber)
extra.set("date", date)
extra.set("gitCommitHash", "fabric")

version = String.format("%s-%s", rootVersion, buildNumber)
group = "com.fastasyncworldedit"

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(arrayOf("-Xmaxerrs", "1000"))
    }
}
