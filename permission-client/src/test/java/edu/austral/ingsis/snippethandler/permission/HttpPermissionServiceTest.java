package edu.austral.ingsis.snippethandler.permission;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class HttpPermissionServiceTest {

    private static final UUID ID = UUID.fromString("7c3e9f1a-0000-4000-8000-000000000001");
    private static final String PATH = "/permissions/" + ID;

    private HttpPermissionService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        RestClient client =
                RestClient.builder()
                        .baseUrl(wireMock.getHttpBaseUrl())
                        .requestFactory(
                                new JdkClientHttpRequestFactory(
                                        HttpClient.newBuilder()
                                                .version(HttpClient.Version.HTTP_1_1)
                                                .build()))
                        .build();
        service = new HttpPermissionService(client);
    }

    @Test
    void registersOwner() {
        stubFor(post("/permissions").willReturn(aResponse().withStatus(201)));

        service.registerOwner(ID);

        verify(
                postRequestedFor(urlEqualTo("/permissions"))
                        .withRequestBody(equalToJson("{\"snippetId\": \"" + ID + "\"}")));
    }

    @Test
    void registerOwnerConflictIsAFailure() {
        stubFor(post("/permissions").willReturn(aResponse().withStatus(409)));

        assertThrows(PermissionServiceException.class, () -> service.registerOwner(ID));
    }

    @Test
    void readsRole() {
        stubFor(get(PATH).willReturn(okJson("{\"role\": \"READER\"}")));

        assertEquals(Role.READER, service.roleOf(ID));
    }

    @Test
    void missingRoleIsNone() {
        stubFor(get(PATH).willReturn(okJson("{}")));
        assertEquals(Role.NONE, service.roleOf(ID));

        stubFor(get(PATH).willReturn(ok()));
        assertEquals(Role.NONE, service.roleOf(ID));
    }

    @Test
    void unknownOrForbiddenSnippetIsNone() {
        stubFor(get(PATH).willReturn(notFound()));
        assertEquals(Role.NONE, service.roleOf(ID));

        stubFor(get(PATH).willReturn(forbidden()));
        assertEquals(Role.NONE, service.roleOf(ID));
    }

    @Test
    void roleFailureIsWrapped() {
        stubFor(get(PATH).willReturn(serverError()));

        assertThrows(PermissionServiceException.class, () -> service.roleOf(ID));
    }

    @Test
    void sharesWithUser() {
        stubFor(post(PATH + "/share").willReturn(aResponse().withStatus(204)));

        service.share(ID, "auth0|bob");

        verify(
                postRequestedFor(urlEqualTo(PATH + "/share"))
                        .withRequestBody(equalToJson("{\"userId\": \"auth0|bob\"}")));
    }

    @Test
    void sharingTwiceIsNotAnError() {
        stubFor(post(PATH + "/share").willReturn(aResponse().withStatus(409)));

        assertDoesNotThrow(() -> service.share(ID, "auth0|bob"));
    }

    @Test
    void shareRefusedOrFailing() {
        stubFor(post(PATH + "/share").willReturn(forbidden()));
        assertThrows(PermissionDeniedException.class, () -> service.share(ID, "auth0|bob"));

        stubFor(post(PATH + "/share").willReturn(aResponse().withStatus(400)));
        assertThrows(PermissionServiceException.class, () -> service.share(ID, "auth0|bob"));

        stubFor(post(PATH + "/share").willReturn(serverError()));
        assertThrows(PermissionServiceException.class, () -> service.share(ID, "auth0|bob"));
    }

    @Test
    void listsAccessibleSnippets() {
        stubFor(
                get(urlEqualTo("/permissions/snippets?relation=shared"))
                        .willReturn(
                                okJson("[{\"snippetId\": \"" + ID + "\", \"role\": \"READER\"}]")));
        stubFor(get(urlEqualTo("/permissions/snippets?relation=owner")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/permissions/snippets?relation=all")).willReturn(ok()));

        assertEquals(List.of(ID), service.accessibleSnippets(Relation.SHARED));
        assertEquals(List.of(), service.accessibleSnippets(Relation.OWNER));
        assertEquals(List.of(), service.accessibleSnippets(Relation.ALL));
    }

    @Test
    void removesSnippet() {
        stubFor(delete(PATH).willReturn(aResponse().withStatus(204)));

        service.removeSnippet(ID);

        verify(deleteRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void removeRefusedIsDenied() {
        stubFor(delete(PATH).willReturn(forbidden()));

        assertThrows(PermissionDeniedException.class, () -> service.removeSnippet(ID));
    }

    @Test
    void rolesAreOrdered() {
        assertTrue(Role.OWNER.atLeast(Role.READER));
        assertTrue(Role.READER.atLeast(Role.READER));
        assertFalse(Role.READER.atLeast(Role.OWNER));
        assertFalse(Role.NONE.atLeast(Role.READER));
    }
}
