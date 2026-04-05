plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugins.spring.boot.get().let { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" })
    implementation(libs.plugins.spring.dependency.management.get().let { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" })
}
