import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.neoruaa.glasmio.sample"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.neoruaa.glasmio.sample"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
}
dependencies {
    implementation(project(":glasmio-scaffold"))
    implementation(project(":glasmio-preference"))
    implementation("androidx.activity:activity-compose:1.13.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

/** Host-owned symbol fonts are generated only into local debug assets. */
abstract class GenerateSymbolFontAssets : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFont: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        sourceFont.orNull?.asFile?.let { font ->
            val signature = font.inputStream().use { it.readNBytes(4).toList() }
            require(signature in listOf(
                listOf<Byte>(0, 1, 0, 0),
                "OTTO".toByteArray().toList(),
                "ttcf".toByteArray().toList(),
                "true".toByteArray().toList(),
            )) { "glasmio.symbolFont must point to an OpenType or TrueType font." }
            font.copyTo(output.resolve("glasmio_symbols.ttf"), overwrite = true)
        }
    }
}

val localSymbolFont = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText.orElse("").map { contents ->
        Properties().apply { load(contents.reader()) }.getProperty("glasmio.symbolFont", "")
    }
val symbolFontPath = providers.gradleProperty("glasmio.symbolFont")
    .orElse(localSymbolFont).map { it.trim() }.filter { it.isNotEmpty() }
val generateSymbolFontAssets = tasks.register<GenerateSymbolFontAssets>("generateSymbolFontAssets") {
    sourceFont.set(layout.file(symbolFontPath.map { File(it) }))
    outputDirectory.set(layout.buildDirectory.dir("generated/symbol-font/debug/assets"))
}
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateSymbolFontAssets,
            GenerateSymbolFontAssets::outputDirectory,
        )
    }
}
