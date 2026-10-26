plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android { namespace = "io.github.neoruaa.glasmio.core" }
dependencies {
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation:1.12.1")
    api("androidx.compose.material3:material3:1.4.0")
    testImplementation("junit:junit:4.13.2")
    api("io.github.kyant0:capsule:2.1.3")
}
