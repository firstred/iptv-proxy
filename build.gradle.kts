import java.io.FileInputStream
import java.time.Instant
import java.util.Properties

plugins {
    java
    application
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sentry)
    alias(libs.plugins.shadow)
    alias(libs.plugins.jib)
    alias(libs.plugins.buildconfig)
}

val localProperties = Properties()
localProperties.load(FileInputStream(rootProject.file("local.properties")))

group = "io.github.firstred"
version = "0.4.1-beta.1"

application {
    mainClass.set("$group.iptvproxy.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.wrapper {
    gradleVersion = "8.14"
    distributionType = Wrapper.DistributionType.ALL
}

buildConfig {
    packageName("io.github.firstred.iptvproxy")

    buildConfigField("APP_NAME", project.name)
    buildConfigField("APP_VERSION", "${project.version}")
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

if ((localProperties["org.opencontainers.image.title"] as String).isNotEmpty()) jib {
    to {
        image = "${localProperties["org.opencontainers.image.location"]}"

        // Convert major.minor.patch to ["$major", "$major.$minor", "$major.$minor.$patch", "latest"]
        tags = if (Regex("""^\d+\.\d+\.\d+$""") matches "${project.version}") {
            "$version"
                .split(".")
                .foldIndexed(listOf<String>()) { index, acc, s ->
                    acc + if (index == 0) s else acc[index - 1] + "." + s
                }
                .let { it + "latest" }
                .toSet()
        } else {
            setOf("$version")
        }
    }
    from {
        image = "docker.io/bellsoft/liberica-openjdk-alpine:21"

        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
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
            "-Dlog.level=warn",
            "-XX:+UseZGC",
            "-XX:+ZGenerational",
            "-XX:+AlwaysPreTouch",
            "-XX:+UseStringDeduplication",
        )
        user = "1000"
        group = "1000"
        workingDirectory = "/app"
        ports = listOf("8080")

        creationTime = Instant.now().toString()
    }
}

if ((localProperties["sentry.auth.token"] as String).isNotEmpty()) sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    includeSourceContext = true

    // Includes the source code of native code when uploading native symbols for Sentry.
    // This executes sentry-cli with the --include-sources param. automatically so
    // you don't need to do it manually. This only works with uploadNativeSymbols enabled.
    //
    // Default is disabled.
    includeNativeSources = true
    // Enables the automatic configuration of Native Symbols for Sentry.
    // This executes sentry-cli automatically so you don't need to do it manually.
    //
    // Default is disabled.
    uploadNativeSymbols = true

    org = localProperties["sentry.org"].toString()
    projectName = localProperties["sentry.project"].toString()
    authToken = localProperties["sentry.auth.token"].toString()
}

dependencies {
    // logging
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.log4j)
    implementation(libs.slf4j.jcl)
    implementation(libs.slf4j.jul)
    implementation(libs.logback)
    implementation(libs.janino)

    // commons
    implementation(libs.apache.commons.codec)
    implementation(libs.apache.commons.io)
    implementation(libs.apache.commons.text)

    // kotlin
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.io)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.yaml)
    implementation(libs.kotlinx.serialization.xml)
    implementation(libs.kotlinx.serialization.xml.core)

    // ktor
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.client.contentNegotiation.json)
    implementation(libs.ktor.client.contentNegotiation.xml)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.engine.java)
    implementation(libs.ktor.client.engine.okhttp)
    implementation(libs.ktor.client.logging)

    implementation(libs.ktor.server.autoHeadResponse)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.defaultHeaders)
    implementation(libs.ktor.server.engine.cio)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.prometheus)

    // koin
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)
    implementation(platform(libs.koin.bom))

    // database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.migration)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)

    implementation(libs.database.sqlite)

    // app specific
    implementation(libs.dotenv)
    implementation(libs.clikt)
    implementation(libs.cryptography)
    implementation(libs.cryptography.provider.jvm)
    implementation(libs.semver)
    implementation(libs.arrow)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.suspendapp)
    implementation(libs.kache)
    implementation(libs.kache.file)

    // tests
    testImplementation(libs.kotlin.test)
}
