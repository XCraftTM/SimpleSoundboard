pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
    }
}

rootProject.name = "OpenSoundboard"

// Use safe project names
include(":mc_1_21_11")
project(":mc_1_21_11").projectDir = file("fabric/1.21.11")

include(":mc_1_21_10")
project(":mc_1_21_10").projectDir = file("fabric/1.21.10")

include(":mc_1_21_4")
project(":mc_1_21_4").projectDir = file("fabric/1.21.4")
