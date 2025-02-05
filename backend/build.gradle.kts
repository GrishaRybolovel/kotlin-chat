plugins {
    kotlin("jvm") version "1.8.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("com.example.ApplicationKt")
}

repositories {
    mavenCentral()
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("backend")
    archiveClassifier.set("all")
    archiveVersion.set(project.version.toString())
}

dependencies {
    // Ktor server core and Netty engine
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    
    // Content negotiation with Gson serialization
    implementation("io.ktor:ktor-server-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-gson:2.3.0")
    
    // JWT and Authentication
    implementation("io.ktor:ktor-server-auth:2.3.0")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.0")
    
    // WebSockets
    implementation("io.ktor:ktor-server-websockets:2.3.0")
    
    // Exposed ORM and PostgreSQL JDBC driver
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.postgresql:postgresql:42.3.0")
    
    // Auth0 JWT library (if needed explicitly)
    implementation("com.auth0:java-jwt:4.2.1")
    
    // Logging (optional)
    implementation("ch.qos.logback:logback-classic:1.2.11")

    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-server-cors:2.3.0")
}
