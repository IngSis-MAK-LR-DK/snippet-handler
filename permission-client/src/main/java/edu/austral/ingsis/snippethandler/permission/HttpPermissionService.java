package edu.austral.ingsis.snippethandler.permission;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link PermissionService} backed by the permissions service over HTTP (see
 * docs/permission-service-contract.md). The given {@link RestClient} is expected to forward the
 * caller's bearer token.
 */
public class HttpPermissionService implements PermissionService {

    private static final String SNIPPET_PATH = "/permissions/snippets/{id}";

    private final RestClient client;

    public HttpPermissionService(RestClient client) {
        this.client = client;
    }

    @Override
    public void registerOwner(UUID snippetId) {
        call(
                "register owner of " + snippetId,
                () ->
                        client.post()
                                .uri(SNIPPET_PATH + "/owner", snippetId)
                                .retrieve()
                                .toBodilessEntity());
    }

    @Override
    public boolean hasPermission(UUID snippetId, Permission permission) {
        try {
            CheckResponse response =
                    client.get()
                            .uri(
                                    SNIPPET_PATH + "/check?permission={permission}",
                                    snippetId,
                                    permission)
                            .retrieve()
                            .body(CheckResponse.class);
            return response != null && response.allowed();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            return false;
        } catch (RestClientException e) {
            throw failure("check permission over " + snippetId, e);
        }
    }

    @Override
    public void share(UUID snippetId, String userId) {
        call(
                "share " + snippetId,
                () ->
                        client.post()
                                .uri(SNIPPET_PATH + "/shares", snippetId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ShareRequest(userId))
                                .retrieve()
                                .toBodilessEntity());
    }

    @Override
    public List<UUID> accessibleSnippets(Relation relation) {
        SnippetIdsResponse response =
                call(
                        "list snippets",
                        () ->
                                client.get()
                                        .uri("/permissions/snippets?relation={relation}", relation)
                                        .retrieve()
                                        .body(SnippetIdsResponse.class));
        return response == null || response.snippetIds() == null
                ? List.of()
                : List.copyOf(response.snippetIds());
    }

    @Override
    public void removeSnippet(UUID snippetId) {
        call(
                "remove " + snippetId,
                () -> client.delete().uri(SNIPPET_PATH, snippetId).retrieve().toBodilessEntity());
    }

    private <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw failure(operation, e);
        }
    }

    private static PermissionServiceException failure(String operation, RestClientException e) {
        return new PermissionServiceException("Permission service failed to " + operation, e);
    }

    record CheckResponse(boolean allowed) {}

    record ShareRequest(String userId) {}

    record SnippetIdsResponse(List<UUID> snippetIds) {}
}
