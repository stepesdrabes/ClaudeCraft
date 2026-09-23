val libraries = listOf(":agent-api", ":agent-mcp", ":agent-claude-code", ":core")

configure(libraries.map(::project)) {
    apply(plugin = "java-library")

    group = "dev.claudecraft"
    version = property("mod_version") as String

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 8
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Xlint:-serial"))
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("claudecraft.live", System.getProperty("claudecraft.live") ?: "false")
        testLogging { showStandardStreams = true }
    }
}

tasks.register("prism") {
    group = "prism"
    description = "Creates or updates Prism Launcher test instances for a representative set of versions"
    val targets = listOf("26.3-fabric", "26.2-fabric", "1.21.11-fabric", "1.16.5-fabric", "1.21.1-neoforge", "1.20.1-forge")
    dependsOn(targets.map { ":platform:$it:prism" })
}
