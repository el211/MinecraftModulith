val mongoDriverVersion: String by project

dependencies {
    api(project(":modulith-core"))
    api("org.mongodb:mongodb-driver-sync:$mongoDriverVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
