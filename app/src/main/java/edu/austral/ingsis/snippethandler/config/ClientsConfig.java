package edu.austral.ingsis.snippethandler.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import edu.austral.ingsis.snippethandler.asset.AssetStore;
import edu.austral.ingsis.snippethandler.asset.HttpAssetStore;
import edu.austral.ingsis.snippethandler.auth0.Auth0ClientCredentialsToken;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.http.HttpLanguageService;
import edu.austral.ingsis.snippethandler.permission.HttpPermissionService;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.security.BearerTokenForwardingInterceptor;
import edu.austral.ingsis.snippethandler.users.Auth0UserDirectory;
import edu.austral.ingsis.snippethandler.users.UserDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP clients towards the language adapters, the asset service and the permissions service. */
@Configuration
public class ClientsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ClientsConfig.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Adapters get the user's token, or the handler's own one in background work (US 16 tests,
     * revalidation), where there is no user.
     */
    @Bean
    public LanguageRegistry languageRegistry(
            SnippetProperties properties, RestClient.Builder builder) {
        BearerTokenForwardingInterceptor tokens =
                new BearerTokenForwardingInterceptor(serviceToken(properties, builder));
        List<LanguageService> services =
                properties.languages().entrySet().stream()
                        .map(
                                entry ->
                                        (LanguageService)
                                                new HttpLanguageService(
                                                        entry.getKey(),
                                                        Set.copyOf(entry.getValue().versions()),
                                                        entry.getValue().fileExtension(),
                                                        builder.clone()
                                                                .baseUrl(entry.getValue().url())
                                                                .requestFactory(requestFactory())
                                                                .requestInterceptor(tokens)
                                                                .build()))
                        .toList();
        return new LanguageRegistry(services);
    }

    private static Supplier<String> serviceToken(
            SnippetProperties properties, RestClient.Builder builder) {
        SnippetProperties.ServiceToken config = properties.serviceToken();
        if (config == null || !config.configured()) {
            LOG.warn(
                    "AUTH0_SERVICE_CLIENT_ID is not set: background calls to the language"
                            + " adapters will go without a token");
            return () -> null;
        }
        Auth0ClientCredentialsToken token =
                new Auth0ClientCredentialsToken(
                        client(builder, config.auth0Domain()),
                        config.clientId(),
                        config.clientSecret(),
                        config.audience(),
                        Clock.systemUTC());
        return token::get;
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

    @Bean
    public UserDirectory userDirectory(SnippetProperties properties, RestClient.Builder builder) {
        SnippetProperties.Users users = properties.users();
        RestClient client = client(builder, users.auth0Domain());
        Auth0ClientCredentialsToken token =
                new Auth0ClientCredentialsToken(
                        client,
                        users.clientId(),
                        users.clientSecret(),
                        users.auth0Domain() + "/api/v2/",
                        Clock.systemUTC());
        return new Auth0UserDirectory(client, token);
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
