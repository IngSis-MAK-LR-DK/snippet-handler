plugins {
    alias(libs.plugins.cikit.java)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    api(project(":language-api"))
    api("org.springframework:spring-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(libs.wiremock)
    // Un adaptador falso con WebSocket para probar WebSocketStreamRunner: el Tomcat que ya usa la app.
    testImplementation("org.apache.tomcat.embed:tomcat-embed-websocket")
    testImplementation("org.assertj:assertj-core")
}
