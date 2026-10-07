plugins {
    id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
    id("maven-publish")
    id("com.modrinth.minotaur") version "2.+"
    kotlin("jvm") version "2.3.20"
}

version = property("mod_version") as String
group = property("maven_group") as String

repositories {
    mavenCentral()
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API. This is technically optional, but you probably want it anyway.
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // gson and google api
    implementation("com.google.api-client:google-api-client:2.0.0")
    include("com.google.api-client:google-api-client:2.0.0")
    include("com.google.http-client:google-http-client:1.44.1")
    include("com.google.http-client:google-http-client-gson:1.44.2")
    include("io.opencensus:opencensus-api:0.31.1")
    include("io.opencensus:opencensus-contrib-http-util:0.31.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    include("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20220927-2.0.0")
    include("com.google.apis:google-api-services-sheets:v4-rev20220927-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    include("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.auth:google-auth-library-credentials:1.24.1")
    include("com.google.auth:google-auth-library-credentials:1.24.1")
    include("io.grpc:grpc-context:1.27.2")

    // unit tests
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.property("mod_version"),
        "loader_version" to project.property("loader_version"),
        "minecraft_version" to project.property("minecraft_version")
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    jvmToolchain(25)
}

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

modrinth {
    token = System.getenv("MODRINTH_TOKEN")
    projectId = "ELPoIqXP" // This can be the project ID or the slug. Either will work!

    versionNumber = project.property("mod_version") as String
    versionType = "release" // `release`, `beta` or `alpha`
    gameVersions.add(project.property("minecraft_version") as String)

    uploadFile.set(tasks.jar)
    loaders.add("fabric")

    dependencies { // A special DSL for creating dependencies
        // scope.type
        // The scope can be `required`, `optional`, `incompatible`, or `embedded`
        // The type can either be `project` or `version`
        required.project("fabric-api") // Creates a new required dependency on Fabric API
        required.project("fabric-language-kotlin")
    }
}


tasks.test {
    useJUnitPlatform()
}
