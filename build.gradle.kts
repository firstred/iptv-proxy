import java.io.FileInputStream
import java.time.Instant
import java.util.Properties

plugins {
    java
    application
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.jib)
    alias(libs.plugins.versions)
    alias(libs.plugins.versionsFilter)
}

val localProperties = Properties()
localProperties.load(FileInputStream(rootProject.file("local.properties")))

group = "io.github.firstred"
version = "0.2.0"

application {
    mainClass.set("$group.iptvproxy.App")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

versionsFilter {
    exclusiveQualifiers.addAll("dev", "eap", "beta", "alpha")
}

tasks.wrapper {
    gradleVersion = "8.11.1"
    distributionType = Wrapper.DistributionType.ALL
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

if ((localProperties["org.opencontainers.image.title"] as String).isNotEmpty()) jib {
    to {
        image = "${localProperties["org.opencontainers.image.location"]}"
        tags = setOf("latest", "$version")
    }
    from {
        image = "docker.io/bellsoft/liberica-openjdk-alpine"
        version = "21"
    }
    container {
        labels = mapOf(
            "maintainer" to "${localProperties["org.opencontainers.image.authors"]}",
            "org.opencontainers.image.version" to "$version",
            "org.opencontainers.image.title" to "${localProperties["org.opencontainers.image.title"]}",
            "org.opencontainers.image.description" to "${localProperties["org.opencontainers.image.description"]}",
            "org.opencontainers.image.authors" to "${localProperties["org.opencontainers.image.authors"]}",
            "org.opencontainers.image.url" to "${localProperties["org.opencontainers.image.url"]}",
            "org.opencontainers.image.vendor" to "${localProperties["org.opencontainers.image.vendor"]}",
            "org.opencontainers.image.licenses" to "${localProperties["org.opencontainers.image.licenses"]}",
        )
        jvmFlags = listOf(
            "-server",
            "-Djava.awt.headless=true",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=100",
            "-XX:+UseStringDeduplication",
        )
        user = "1000"
        group = "1000"
        workingDirectory = "/app"
        ports = listOf("8080")

        creationTime = Instant.now().toString()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations.forEach {
    it.exclude("org.apache.httpcomponents", "httpclient")
    it.exclude("org.apache.httpcomponents", "httpcore")

    it.exclude("com.sun.mail", "javax.mail")
    it.exclude("javax.activation", "activation")
}

dependencies {
    // logging
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.log4j)
    implementation(libs.slf4j.jcl)
    implementation(libs.slf4j.jul)
    implementation(libs.logback)
    implementation(libs.janino)
    implementation(libs.kotlin.coroutines)

    // app specific
    implementation(libs.undertow)
    implementation(libs.snakeyaml)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.dataformat.xml)
}
