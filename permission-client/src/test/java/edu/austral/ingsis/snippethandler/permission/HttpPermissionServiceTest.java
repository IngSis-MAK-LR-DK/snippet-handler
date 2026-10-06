package edu.austral.ingsis.snippethandler.permission;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
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
    private static final String PATH = "/permissions/snippets/" + ID;

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
        stubFor(post(PATH + "/owner").willReturn(aResponse().withStatus(201)));

        service.registerOwner(ID);

        verify(postRequestedFor(urlEqualTo(PATH + "/owner")));
    }

    @Test
    void registerOwnerFailureIsWrapped() {
        stubFor(post(PATH + "/owner").willReturn(serverError()));

        assertThrows(PermissionServiceException.class, () -> service.registerOwner(ID));
    }

    @Test
    void checksPermission() {
        stubFor(
                get(urlEqualTo(PATH + "/check?permission=WRITE"))
                        .willReturn(okJson("{\"allowed\": true}")));
        stubFor(
                get(urlEqualTo(PATH + "/check?permission=OWNER"))
                        .willReturn(okJson("{\"allowed\": false}")));

        assertTrue(service.hasPermission(ID, Permission.WRITE));
        assertFalse(service.hasPermission(ID, Permission.OWNER));
    }

    @Test
    void unknownOrForbiddenSnippetHasNoPermission() {
        stubFor(get(urlEqualTo(PATH + "/check?permission=READ")).willReturn(notFound()));
        assertFalse(service.hasPermission(ID, Permission.READ));

        stubFor(get(urlEqualTo(PATH + "/check?permission=READ")).willReturn(forbidden()));
        assertFalse(service.hasPermission(ID, Permission.READ));
    }

    @Test
    void checkFailureIsWrapped() {
        stubFor(get(urlEqualTo(PATH + "/check?permission=READ")).willReturn(serverError()));

        assertThrows(
                PermissionServiceException.class, () -> service.hasPermission(ID, Permission.READ));
    }

    @Test
    void sharesWithUser() {
        stubFor(post(PATH + "/shares").willReturn(aResponse().withStatus(201)));

        service.share(ID, "auth0|bob");

        verify(
                postRequestedFor(urlEqualTo(PATH + "/shares"))
                        .withRequestBody(equalToJson("{\"userId\": \"auth0|bob\"}")));
    }

    @Test
    void listsAccessibleSnippets() {
        stubFor(
                get(urlEqualTo("/permissions/snippets?relation=SHARED"))
                        .willReturn(okJson("{\"snippetIds\": [\"" + ID + "\"]}")));
        stubFor(get(urlEqualTo("/permissions/snippets?relation=OWNED")).willReturn(okJson("{}")));

        assertEquals(List.of(ID), service.accessibleSnippets(Relation.SHARED));
        assertEquals(List.of(), service.accessibleSnippets(Relation.OWNED));
    }

    @Test
    void removesSnippet() {
        stubFor(delete(PATH).willReturn(aResponse().withStatus(204)));

        service.removeSnippet(ID);

        verify(deleteRequestedFor(urlEqualTo(PATH)));
    }
}
