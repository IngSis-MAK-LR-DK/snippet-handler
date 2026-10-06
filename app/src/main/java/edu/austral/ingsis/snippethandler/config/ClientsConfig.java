package edu.austral.ingsis.snippethandler.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import edu.austral.ingsis.snippethandler.asset.AssetStore;
import edu.austral.ingsis.snippethandler.asset.HttpAssetStore;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.http.HttpLanguageService;
import edu.austral.ingsis.snippethandler.permission.HttpPermissionService;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.security.BearerTokenForwardingInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP clients towards the language adapters, the asset service and the permissions service. */
@Configuration
public class ClientsConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public LanguageRegistry languageRegistry(
            SnippetProperties properties, RestClient.Builder builder) {
        List<LanguageService> services =
                properties.languages().entrySet().stream()
                        .map(
                                entry ->
                                        (LanguageService)
                                                new HttpLanguageService(
                                                        entry.getKey(),
                                                        Set.copyOf(entry.getValue().versions()),
                                                        entry.getValue().fileExtension(),
                                                        client(builder, entry.getValue().url())))
                        .toList();
        return new LanguageRegistry(services);
    }

    @Bean
    public AssetStore assetStore(SnippetProperties properties, RestClient.Builder builder) {
        return new HttpAssetStore(
                client(builder, properties.asset().url()), properties.asset().container());
    }

    @Bean
    public PermissionService permissionService(
            SnippetProperties properties, RestClient.Builder builder) {
        RestClient client =
                builder.clone()
                        .baseUrl(properties.permission().url())
                        .requestFactory(requestFactory())
                        .requestInterceptor(new BearerTokenForwardingInterceptor())
                        .build();
        return new HttpPermissionService(client);
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl) {
        return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory()).build();
    }

    // HTTP/1.1 explicito: el cliente de JDK intenta upgrade a h2c por defecto y algunos
    // servidores cortan la conexion en requests con body.
    private static ClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(CONNECT_TIMEOUT)
                                .build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
