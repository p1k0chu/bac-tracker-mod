plugins {
	id("fabric-loom") version "1.11-SNAPSHOT"
	id("maven-publish")
	id("com.modrinth.minotaur") version "2.+"
	kotlin("jvm") version "2.2.0"
}

val mod_version: String by project
val minecraft_version: String by project
val loader_version: String by project
val maven_group: String by project
val yarn_mappings: String by project
val fabric_kotlin_version: String by project
val archives_base_name: String by project
val fabric_version: String by project

version = mod_version
group = maven_group

base {
	archivesName = archives_base_name
}

repositories {
	mavenCentral()
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${minecraft_version}")
	mappings("net.fabricmc:yarn:${yarn_mappings}:v2")
	modImplementation("net.fabricmc:fabric-loader:${loader_version}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")
	modImplementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")

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
	filesMatching("fabric.mod.json") {
		expand(
			"version" to mod_version,
			"loader_version" to loader_version,
			"minecraft_version" to minecraft_version
		)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
	jvmToolchain(21)
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_$archives_base_name" }
	}
}

modrinth {
	token = System.getenv("MODRINTH_TOKEN")
	projectId = "ELPoIqXP" // This can be the project ID or the slug. Either will work!

	versionNumber = mod_version
	versionType = "release" // `release`, `beta` or `alpha`
	gameVersions.add(minecraft_version)

	uploadFile.set(tasks.remapJar)
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
