dependencies {
    api(project(":modulith-core"))
    api("org.junit.jupiter:junit-jupiter-api:5.11.4")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
