plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.serialization)
	application
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.mcp.sdk)
	implementation(libs.xemantic.ai.tool.schema)
	implementation(libs.ktor.client.core)
	implementation(libs.ktor.client.cio)
	implementation(libs.ktor.client.content.negotiation)
	implementation(libs.ktor.serialization.kotlinx.json)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.mockito.core)
	testImplementation(libs.mockito.kotlin)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.ktor.client.mock)
	testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
	jvmToolchain(21)
}

application {
	mainClass = "com.homebox.mcp.MainKt"
}

tasks.test {
	useJUnitPlatform()
}
