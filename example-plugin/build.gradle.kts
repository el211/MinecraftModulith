val paperApiVersion: String by project

dependencies {
    implementation(project(":modulith-paper"))
    implementation(project(":modulith-events-sqlite"))
    implementation(project(":modulith-events-jdbc"))
    implementation(project(":modulith-events-mongodb"))
    annotationProcessor(project(":modulith-processor"))
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)

    archiveBaseName.set("minecraft-modulith-example")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
}
