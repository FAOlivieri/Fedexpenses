plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Optional: keep build output outside synced folders (OneDrive locks files mid-build).
// Set `buildRoot=C:/path/outside/onedrive` in local.properties to enable.
val localProps = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
localProps.getProperty("buildRoot")?.let { root ->
    allprojects { layout.buildDirectory.set(file("$root/${project.name}")) }
}
