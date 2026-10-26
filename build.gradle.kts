import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension


plugins {
    id("com.android.library") version "9.4.1" apply false
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")
configure<LibraryExtension> {
    compileSdk = 37
    defaultConfig { minSdk = 33; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
    publishing { singleVariant("release") { withSourcesJar(); withJavadocJar() } }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                pom {
                    name.set(project.name)
                    description.set("iOS-inspired Jetpack Compose components and Liquid Glass rendering")
                    url.set("https://github.com/NEORUAA/Glasmio")
                    licenses { license { name.set("Common Public Attribution License 1.0"); url.set("https://opensource.org/license/cpal_1-0") } }
                    developers { developer { id.set("NEORUAA"); name.set("NEORUAA"); url.set("https://github.com/NEORUAA") } }
                    scm { url.set("https://github.com/NEORUAA/Glasmio"); connection.set("scm:git:https://github.com/NEORUAA/Glasmio.git"); developerConnection.set("scm:git:ssh://git@github.com/NEORUAA/Glasmio.git") }
                }
            }
        }
        repositories { maven { name = "staging"; url = rootProject.layout.buildDirectory.dir("maven").get().asFile.toURI() } }
    }
    configure<SigningExtension> {
        val key = providers.environmentVariable("MAVEN_SIGNING_KEY").orNull
        if (key != null) {
            useInMemoryPgpKeys(key, providers.environmentVariable("MAVEN_SIGNING_PASSWORD").orNull)
            sign(project.extensions.getByType<PublishingExtension>().publications)
        }
    }
}

    }
}
