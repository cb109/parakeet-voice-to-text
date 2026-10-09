import java.net.URI

plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.whistle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.whistle"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // The Needle engine ships prebuilt for android-arm64 only in this setup.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake { arguments += "-DANDROID_STL=c++_static" }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Engine (static lib + header) and Whistle weights from Hugging Face; fetched once, then cached in the source tree.
val whistleDownloads = mapOf(
    "src/main/cpp/needle/arm64-v8a/libneedle.a" to
        "https://huggingface.co/Cactus-Compute/needle3/resolve/main/android-arm64/libneedle.a",
    "src/main/cpp/needle/arm64-v8a/needle.h" to
        "https://huggingface.co/Cactus-Compute/needle3/resolve/main/android-arm64/needle.h",
    "src/main/assets/whistle.cact" to
        "https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact",
).mapKeys { file(it.key) }

val fetchWhistle by tasks.registering {
    description = "Downloads the Needle engine for android-arm64 and the Whistle model."
    val downloads = whistleDownloads
    outputs.files(downloads.keys)
    doLast {
        downloads.forEach { (target, url) ->
            if (target.exists()) return@forEach
            target.parentFile.mkdirs()
            val partial = File(target.path + ".part")
            URI(url).toURL().openStream().use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            check(partial.renameTo(target)) { "Could not write $target" }
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchWhistle) }
