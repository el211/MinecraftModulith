val paperApiVersion: String by project

dependencies {
    api(project(":modulith-core"))
    implementation("io.github.classgraph:classgraph:4.8.179")
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}
