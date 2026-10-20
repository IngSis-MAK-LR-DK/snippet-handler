package edu.austral.ingsis.snippethandler.asset;

import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link AssetStore} backed by the course asset service ({@code /v1/asset/{container}/{key}}). */
public class HttpAssetStore implements AssetStore {

    private static final String ASSET_PATH = "/v1/asset/{container}/{key}";

    private final RestClient client;
    private final String container;

    /**
     * @param client a {@link RestClient} whose base URL points to the asset service
     * @param container bucket container where the snippets are stored
     */
    public HttpAssetStore(RestClient client, String container) {
        this.client = client;
        this.container = container;
    }

    @Override
    public void put(String key, String content) {
        call(
                "store",
                key,
                () ->
                        client.put()
                                .uri(ASSET_PATH, container, key)
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(content)
                                .retrieve()
                                .toBodilessEntity());
    }

    @Override
    public Optional<String> get(String key) {
        try {
            String content =
                    client.get().uri(ASSET_PATH, container, key).retrieve().body(String.class);
            return Optional.of(content == null ? "" : content);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw failure("read", key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.delete().uri(ASSET_PATH, container, key).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw failure("delete", key, e);
            }
        } catch (RestClientException e) {
            throw failure("delete", key, e);
        }
    }

    private void call(String operation, String key, Supplier<?> request) {
        try {
            request.get();
        } catch (RestClientException e) {
            throw failure(operation, key, e);
        }
    }

    private AssetStoreException failure(String operation, String key, RestClientException e) {
        return new AssetStoreException(
                "Could not %s asset '%s' in container '%s'".formatted(operation, key, container),
                e);
    }
}
