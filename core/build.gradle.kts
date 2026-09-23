dependencies {
    api(project(":agent-api"))
    implementation(project(":agent-mcp"))
    implementation(project(":agent-claude-code"))
}

tasks.named<JavaCompile>("compileTestJava") { options.release = 17 }
