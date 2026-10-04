plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("minecraftModulith") {
            id = "dev.oreo.minecraft-modulith"
            implementationClass = "dev.oreo.modulith.gradle.MinecraftModulithPlugin"
            displayName = "MinecraftModulith"
            description = "Compile-time module verification and module documentation"
        }
    }
}
