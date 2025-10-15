plugins {
	alias(libs.plugins.ktlint)
	alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
	pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
		apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

		ktlint {
			kotlinScriptAdditionalPaths {
				include(
					fileTree(
						mapOf(
							"dir" to projectDir,
							"include" to listOf("*.gradle.kts", "gradle/**/*.gradle.kts"),
						),
					),
				)
			}
			filter {
				exclude("**/generated/**")
			}
		}
	}
}

tasks.register("containerMaintenance") {
	group = "build"
	description = "Builds the classes to ensure dependencies are loaded and classes cached for when the agent is running"
	dependsOn(":app:testClasses")
}
