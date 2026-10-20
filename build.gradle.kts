plugins {
    // Todas las reglas del equipo: spotless, checkstyle, JUnit 5, JaCoCo >= 80%.
    alias(libs.plugins.cikit.java) apply false
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "edu.austral.ingsis"
    version = "0.1.0"
}
