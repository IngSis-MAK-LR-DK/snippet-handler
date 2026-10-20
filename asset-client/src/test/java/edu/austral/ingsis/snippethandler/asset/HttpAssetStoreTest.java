package edu.austral.ingsis.snippethandler.asset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import java.util.Optional;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class HttpAssetStoreTest {

    private static final String PATH = "/v1/asset/snippets/abc";

    private HttpAssetStore store;

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
        store = new HttpAssetStore(client, "snippets");
    }

    @Test
    void putUploadsContent() {
        stubFor(put(PATH).willReturn(aResponse().withStatus(201)));

        store.put("abc", "println(1);");

        verify(putRequestedFor(urlEqualTo(PATH)).withRequestBody(equalTo("println(1);")));
    }

    @Test
    void putFailureIsWrapped() {
        stubFor(put(PATH).willReturn(serverError()));

        AssetStoreException e =
                assertThrows(AssetStoreException.class, () -> store.put("abc", "x"));
        assertEquals("Could not store asset 'abc' in container 'snippets'", e.getMessage());
    }

    @Test
    void getReturnsContent() {
        stubFor(get(PATH).willReturn(ok("println(1);")));

        assertEquals(Optional.of("println(1);"), store.get("abc"));
    }

    @Test
    void getEmptyBodyIsEmptyContent() {
        stubFor(get(PATH).willReturn(ok()));

        assertEquals(Optional.of(""), store.get("abc"));
    }

    @Test
    void getMissingIsEmpty() {
        stubFor(get(PATH).willReturn(notFound()));

        assertEquals(Optional.empty(), store.get("abc"));
    }

    @Test
    void getFailureIsWrapped() {
        stubFor(get(PATH).willReturn(serverError()));

        assertThrows(AssetStoreException.class, () -> store.get("abc"));
    }

    @Test
    void deleteIgnoresMissingAssets() {
        stubFor(delete(PATH).willReturn(notFound()));

        assertDoesNotThrow(() -> store.delete("abc"));
    }

    @Test
    void deleteSucceeds() {
        stubFor(delete(PATH).willReturn(aResponse().withStatus(204)));

        assertDoesNotThrow(() -> store.delete("abc"));
    }

    @Test
    void deleteFailuresAreWrapped() {
        stubFor(delete(PATH).willReturn(aResponse().withStatus(403)));
        assertThrows(AssetStoreException.class, () -> store.delete("abc"));

        stubFor(delete(PATH).willReturn(serverError()));
        assertThrows(AssetStoreException.class, () -> store.delete("abc"));
    }
}
