plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android { namespace = "io.github.neoruaa.glasmio.scaffold" }
dependencies {
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation:1.12.1")
    api("androidx.compose.material3:material3:1.4.0")
    testImplementation("junit:junit:4.13.2")
    api(project(":glasmio-ui"))
    api("androidx.navigation3:navigation3-runtime:1.2.0")
    api("androidx.navigationevent:navigationevent-compose:1.1.2")
    api("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("androidx.activity:activity-compose:1.13.0")
}
