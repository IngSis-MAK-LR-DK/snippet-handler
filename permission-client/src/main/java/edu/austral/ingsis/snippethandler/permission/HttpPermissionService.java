package edu.austral.ingsis.snippethandler.permission;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link PermissionService} backed by the permissions service over HTTP (see
 * docs/permission-service-contract.md). It takes two {@link RestClient}s: {@code client} is
 * expected to forward the caller's bearer token, and {@code serviceClient} to authenticate with the
 * handler's own token (client credentials), which is what registering an owner requires.
 */
public class HttpPermissionService implements PermissionService {

    private static final String SNIPPET_PATH = "/permissions/{snippetId}";

    private final RestClient client;
    private final RestClient serviceClient;

    public HttpPermissionService(RestClient client, RestClient serviceClient) {
        this.client = client;
        this.serviceClient = serviceClient;
    }

    @Override
    public void registerOwner(UUID snippetId, String ownerId) {
        call(
                "register owner of " + snippetId,
                () ->
                        serviceClient
                                .post()
                                .uri("/permissions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new RegisterRequest(snippetId.toString(), ownerId))
                                .retrieve()
                                .toBodilessEntity());
    }

    @Override
    public Role roleOf(UUID snippetId) {
        try {
            RoleResponse response =
                    client.get().uri(SNIPPET_PATH, snippetId).retrieve().body(RoleResponse.class);
            return response == null || response.role() == null ? Role.NONE : response.role();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            return Role.NONE;
        } catch (RestClientException e) {
            throw failure("check role over " + snippetId, e);
        }
    }

    @Override
    public void share(UUID snippetId, String userId) {
        try {
            client.post()
                    .uri(SNIPPET_PATH + "/share", snippetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ShareRequest(userId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.CONFLICT) {
                throw clientError("share " + snippetId, e);
            }
        } catch (RestClientException e) {
            throw failure("share " + snippetId, e);
        }
    }

    @Override
    public List<UUID> accessibleSnippets(Relation relation) {
        SnippetAccess[] response =
                call(
                        "list snippets",
                        () ->
                                client.get()
                                        .uri(
                                                "/permissions/snippets?relation={relation}",
                                                relation.name().toLowerCase(Locale.ROOT))
                                        .retrieve()
                                        .body(SnippetAccess[].class));
        return response == null
                ? List.of()
                : Arrays.stream(response)
                        .map(access -> UUID.fromString(access.snippetId()))
                        .toList();
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
        } catch (HttpClientErrorException e) {
            throw clientError(operation, e);
        } catch (RestClientException e) {
            throw failure(operation, e);
        }
    }

    private static RuntimeException clientError(String operation, HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
            return new PermissionDeniedException("Permission service refused to " + operation, e);
        }
        return failure(operation, e);
    }

    private static PermissionServiceException failure(String operation, RestClientException e) {
        return new PermissionServiceException("Permission service failed to " + operation, e);
    }

    record RegisterRequest(String snippetId, String ownerId) {}

    record RoleResponse(Role role) {}

    record ShareRequest(String userId) {}

    record SnippetAccess(String snippetId, Role role) {}
}
