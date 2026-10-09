pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx publishes its Android AAR only as a GitHub release asset.
        ivy("https://github.com/") {
            patternLayout { artifact("[organisation]/[module]/releases/download/v[revision]/[module]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeGroup("k2-fsa") }
        }
    }
}

rootProject.name = "parakeet-voice-to-text"
include(":app")
