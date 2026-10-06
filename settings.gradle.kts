// Le dice a Gradle de donde bajar el plugin de ci-kit (GitHub Packages pide login incluso
// para leer). En CI las credenciales vienen del workflow; en tu PC, de ~/.gradle/gradle.properties.
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/IngSis-MAK-LR-DK/ci-kit")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "snippet-handler-service"

include("language-api", "language-http", "asset-client", "permission-client", "app")
