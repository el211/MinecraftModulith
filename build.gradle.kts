allprojects {
    group = "dev.oreo.modulith"
    version = "0.4.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java-library")

    if (name != "example-plugin" && name != "modulith-gradle-plugin") {
        apply(plugin = "maven-publish")

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                }
            }

            repositories {
                maven {
                    name = "oreostudios"
                    url = uri(
                        if (version.toString().endsWith("-SNAPSHOT"))
                            "https://maven.oreostudios.fr/snapshots"
                        else
                            "https://maven.oreostudios.fr/releases"
                    )
                    credentials {
                        username = providers.gradleProperty("oreostudiosUsername")
                            .orElse(providers.environmentVariable("MAVEN_USERNAME"))
                            .orNull
                        password = providers.gradleProperty("oreostudiosPassword")
                            .orElse(providers.environmentVariable("MAVEN_PASSWORD"))
                            .orNull
                    }
                }
            }
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}