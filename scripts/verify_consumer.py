#!/usr/bin/env python3
"""Build a fresh app using Maven coordinates and transitive dependencies."""
from pathlib import Path
import argparse
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--central', action='store_true', help='Resolve published artifacts exclusively from Maven Central')
args = parser.parse_args()
folder = ROOT / ('build/consumer-central' if args.central else 'build/consumer')
if folder.exists():
    shutil.rmtree(folder)
folder.mkdir(parents=True)
version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('VERSION_NAME='))
repository = '' if args.central else 'maven { url = uri("' + (ROOT / 'build/maven').as_uri() + '") }; '
(folder / 'settings.gradle.kts').write_text('''pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { ''' + repository + '''google(); mavenCentral() }
}
rootProject.name = "GlasmioConsumer"
''')
(folder / 'build.gradle.kts').write_text('''plugins {
    id("com.android.application") version "9.4.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}
android {
    namespace = "example.consumer"
    compileSdk = 37
    defaultConfig { applicationId = "example.consumer"; minSdk = 33; targetSdk = 37 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation("io.github.neoruaa.glasmio:glasmio-scaffold:''' + version + '''")
    implementation("io.github.neoruaa.glasmio:glasmio-preference:''' + version + '''")
    implementation("androidx.activity:activity-compose:1.13.0")
}
''')
source = folder / 'src/main/java/example/consumer'
source.mkdir(parents=True)
(source / 'MainActivity.kt').write_text('''package example.consumer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.scaffold.GlasmioScaffold
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.preference.GlasmioPreferenceEntry
import io.github.neoruaa.glasmio.icons.SfIcon
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlasmioTheme {
                GlasmioBackdropHost(Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize()) },
                    overlayContent = {
                        GlasmioScaffold { padding ->
                            Column(Modifier.padding(padding)) {
                                GlasmioPreferenceEntry(title = { Text("Glasmio") }, trailingContent = { SfIcon("checkmark", null) })
                                GlasmioButton(onClick = {}) { Text("Glasmio") }
                            }
                        }
                    },
                )
            }
        }
    }
}
''')
(folder / 'src/main/AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name=".MainActivity" android:exported="true" /></application></manifest>')
if (ROOT / 'local.properties').is_file():
    shutil.copyfile(ROOT / 'local.properties', folder / 'local.properties')
shutil.copyfile(ROOT / 'gradle.properties', folder / 'gradle.properties')
subprocess.run([str(ROOT / 'gradlew'), '--project-dir', str(folder), 'assembleDebug', '--refresh-dependencies', '--console=plain'], cwd=ROOT, check=True)
print('Fresh ' + ('Maven Central' if args.central else 'staged Maven') + ' consumer built successfully.')
