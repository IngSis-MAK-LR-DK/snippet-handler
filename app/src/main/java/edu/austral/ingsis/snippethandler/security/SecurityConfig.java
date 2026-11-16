package edu.austral.ingsis.snippethandler.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.observability.UserMdcFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Every endpoint but the health check requires an Auth0 access token. Issuer and audience are
 * validated from {@code spring.security.oauth2.resourceserver.jwt.*}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // The token can also come as WebSocket subprotocol (streamed runs, US 9 and 10).
                .oauth2ResourceServer(
                        oauth ->
                                oauth.bearerTokenResolver(new WebSocketBearerTokenResolver())
                                        .jwt(withDefaults()))
                .addFilterAfter(new UserMdcFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SnippetProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(
                properties.cors() == null ? List.of() : properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of("Location", "Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
