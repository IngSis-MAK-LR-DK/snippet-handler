plugins {
    alias(libs.plugins.cikit.java)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    api("org.springframework:spring-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(libs.wiremock)
}
