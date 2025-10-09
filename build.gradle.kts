import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
        extensions.configure<KtlintExtension> {
            filter {
                exclude("**/generated/**")
            }
        }
    }
}
