import org.gradle.api.tasks.Copy
import java.io.File

allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.terraformersmc.com/")
        maven("https://maven.maxhenkel.de/repository/public")
        maven("https://api.modrinth.com/maven") {
            content { includeGroup("maven.modrinth") }
        }
    }
}

plugins {
    base
    id("fabric-loom") version "1.14-SNAPSHOT" apply false
    kotlin("jvm") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
}

tasks.named("assemble") {
    dependsOn("collectJars")
}

val jarsDir: Any = layout.projectDirectory.dir("jars")

// Collect all remapped jars from subprojects that apply Loom
val collectJars = tasks.register("collectJars", Copy::class) {
    group = "build"
    description = "Collect remapped jars from all Loom subprojects into /jars."

    into(jarsDir)

    // Only from projects that actually have Loom's remapJar task
    subprojects.forEach { p ->
        val remap = p.tasks.findByName("remapJar")
        if (remap != null) {
            dependsOn(remap)

            // remapJar produces the distributable mod jar
            from(p.tasks.named("remapJar").map { it.outputs.files }) {}
        }
    }
}
