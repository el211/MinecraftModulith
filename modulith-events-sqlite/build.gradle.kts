val sqliteJdbcVersion: String by project

dependencies {
    api(project(":modulith-core"))
    runtimeOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
}
