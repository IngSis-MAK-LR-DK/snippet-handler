plugins {
    alias(libs.plugins.cikit.java)
    alias(libs.plugins.spring.boot)
}

dependencies {
    // BOM como platform de Gradle (no el plugin io.spring.dependency-management): asi las
    // versiones de JUnit que fija ci-kit se alinean con las de Spring Boot.
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":language-http"))
    implementation(project(":asset-client"))
    implementation(project(":permission-client"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.awaitility:awaitility")
    testImplementation(libs.wiremock)
}

// Solo se publica el jar ejecutable de Spring Boot.
tasks.jar {
    enabled = false
}

// El main de Spring Boot no tiene logica propia: no cuenta para la cobertura.
val coveredClasses =
    sourceSets.main.get().output.classesDirs.asFileTree.matching {
        exclude("**/SnippetHandlerApplication*")
    }

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(coveredClasses)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(coveredClasses)
}
