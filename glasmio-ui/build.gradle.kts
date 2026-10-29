plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android { namespace = "io.github.neoruaa.glasmio.ui" }
dependencies {
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation:1.12.1")
    api("androidx.compose.material3:material3:1.4.0")
    testImplementation("junit:junit:4.13.2")
    api(project(":glasmio-glass"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Keep cross-module GPU fixtures in the rendering suite without publishing backend internals.
val glassBackendBuildDirectory = project(":glasmio-glass").layout.buildDirectory
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name == "compileDebugAndroidTestKotlin") {
        friendPaths.from(libraries.filter { it.toPath().startsWith(glassBackendBuildDirectory.get().asFile.toPath()) })
    }
}
