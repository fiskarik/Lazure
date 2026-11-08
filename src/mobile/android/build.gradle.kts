import java.net.URL
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val lazureAbis: List<String> =
    (findProperty("lazure.abis") as String?)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: listOf("arm64-v8a", "x86_64")

android {
    namespace = "com.rassvet.essential"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ru.myessentiality.essential"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Phones are arm64-v8a; x86_64 is for the emulator. A phone-only APK: -Plazure.abis=arm64-v8a
            abiFilters += lazureAbis
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    ndkVersion = "27.1.12297006"

    signingConfigs {
        val storeFile = localProps.getProperty("lazure.storeFile")
        if (storeFile != null) {
            create("lazureRelease") {
                this.storeFile = rootProject.file(storeFile)
                storePassword = localProps.getProperty("lazure.storePassword")
                keyAlias = localProps.getProperty("lazure.keyAlias")
                keyPassword = localProps.getProperty("lazure.keyPassword")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Installable without a team keystore: signed with the debug key unless lazure.storeFile etc. are set in
            // local.properties. Kotlin code runs noticeably faster than in a debuggable build (DSP, clustering).
            signingConfig = signingConfigs.findByName("lazureRelease") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { noCompress += listOf("onnx", "litertlm") }
    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Optional JVM run of the real speech pipeline (SpeechPipelineJvmTest): sherpa-onnx Linux JNI directory.
            System.getenv("LAZURE_SHERPA_JNI_DIR")?.let { test.systemProperty("java.library.path", it) }
        }
    }
}

val downloadLiteRtGpuSamplers by tasks.registering {
    val samplerVersion = libs.versions.litertlm.get()
    val jniRoot = layout.projectDirectory.dir("src/main/jniLibs")
    val targets =
        mapOf(
            "arm64-v8a" to
                "https://media.githubusercontent.com/media/google-ai-edge/LiteRT-LM/v$samplerVersion/" +
                    "prebuilt/android_arm64/libLiteRtTopKOpenClSampler.so",
            "x86_64" to
                "https://media.githubusercontent.com/media/google-ai-edge/LiteRT-LM/v$samplerVersion/" +
                    "prebuilt/android_x86_64/libLiteRtTopKOpenClSampler.so",
        )
    inputs.property("samplerVersion", samplerVersion)
    outputs.dirs(jniRoot)
    onlyIf {
        targets.any { (abi, _) ->
            !jniRoot.file("$abi/libLiteRtTopKOpenClSampler.so").asFile.exists()
        }
    }
    doLast {
        targets.forEach { (abi, url) ->
            val outFile = jniRoot.file("$abi/libLiteRtTopKOpenClSampler.so").asFile
            if (outFile.exists()) return@forEach
            outFile.parentFile.mkdirs()
            URL(url).openStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            logger.lifecycle("Downloaded LiteRT GPU sampler: ${outFile.name} ($abi)")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadLiteRtGpuSamplers)
}

configurations.configureEach {
    resolutionStrategy {

        force("androidx.compose.material3:material3:1.5.0-alpha16")
        force("androidx.compose.material3:material3-android:1.5.0-alpha16")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.litertlm.android)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation("ru.noties:jlatexmath-android:0.2.0")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.room:room-testing:2.7.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.room:room-testing:2.7.0")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}


