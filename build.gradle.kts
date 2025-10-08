plugins {
	// Apply once at the root; individual modules reuse the configured instance.
	alias(libs.plugins.ktlint)
}

allprojects {
	apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

	ktlint {
		filter {
			exclude("**/generated/**")
		}
	}
}
