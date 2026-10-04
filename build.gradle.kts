plugins {
    kotlin("jvm") version "2.2.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-netty:3.2.3")
    implementation("io.ktor:ktor-server-html-builder:3.2.3")
    implementation("io.ktor:ktor-server-sessions:3.2.3")
    implementation("io.ktor:ktor-server-status-pages:3.2.3")
    implementation("io.ktor:ktor-server-call-logging:3.2.3")
    implementation("io.ktor:ktor-server-compression:3.2.3")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.google.firebase:firebase-admin:9.5.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("tr.com.marssolar.web.ApplicationKt")
}

tasks.register<JavaExec>("importCatalog") {
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("tr.com.marssolar.web.ImportCatalogKt")
    workingDir = projectDir
}
