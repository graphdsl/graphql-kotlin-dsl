plugins {
    id("com.android.application")
    kotlin("android")
    id("io.github.graphdsl.graphdsl-gradle-plugin")
}

android {
    namespace = "io.github.graphdsl.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.graphdsl.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Must match Kotlin 1.9.24 — see https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The graphdsl plugin auto-wires sources for kotlin.jvm only.
    // Android uses kotlin.android, so we wire the generated sources manually.
    sourceSets {
        getByName("main") {
            java.srcDir("build/generated-sources/graphdsl")
        }
    }
}

graphDsl {
    packageName.set("io.github.graphdsl.android.graphql")
    schemaDir.set("src/main/graphql")
}

// Make Android Kotlin compile tasks depend on DSL generation
tasks.configureEach {
    if (name.startsWith("compile") && name.endsWith("Kotlin")) {
        dependsOn("generateGraphDsl")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
