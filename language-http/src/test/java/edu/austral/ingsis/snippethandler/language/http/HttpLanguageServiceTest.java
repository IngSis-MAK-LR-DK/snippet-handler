package edu.austral.ingsis.snippethandler.language.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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

import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;

import edu.austral.ingsis.snippethandler.language.ExecutionResult;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleDefinition;
import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.language.SyntaxError;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.language.Violation;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class HttpLanguageServiceTest {

    private HttpLanguageService service;

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
        service = new HttpLanguageService("printscript", Set.of("1.0"), "ps", client);
    }

    @Test
    void exposesMetadata() {
        assertEquals("printscript", service.language());
        assertEquals(Set.of("1.0"), service.versions());
        assertEquals("ps", service.fileExtension());
    }

    @Test
    void validateReturnsErrorsWithPosition() {
        stubFor(
                post("/validate")
                        .withRequestBody(equalToJson("{\"content\":\"let x\",\"version\":\"1.0\"}"))
                        .willReturn(
                                okJson(
                                        """
                                        {"valid": false, "errors": [
                                          {"rule": "missing-semicolon", "message": "Expected ';'",
                                           "line": 1, "column": 6}]}
                                        """)));

        ValidationResult result = service.validate("let x", "1.0");

        assertFalse(result.valid());
        assertEquals(
                List.of(new SyntaxError("missing-semicolon", "Expected ';'", 1, 6)),
                result.errors());
    }

    @Test
    void lintSendsRulesAndReturnsViolations() {
        stubFor(
                post("/lint")
                        .willReturn(
                                okJson(
                                        """
                                        {"violations": [{"rule": "camel-case", "message": "Use camelCase",
                                          "line": 1, "column": 5}]}
                                        """)));

        List<Violation> violations =
                service.lint(
                        "let my_var = 1;",
                        "1.0",
                        List.of(new RuleConfig("camel-case", true, null)));

        assertEquals(List.of(new Violation("camel-case", "Use camelCase", 1, 5)), violations);
        verify(
                postRequestedFor(urlEqualTo("/lint"))
                        .withRequestBody(
                                equalToJson(
                                        """
                                        {"content": "let my_var = 1;", "version": "1.0",
                                         "rules": [{"name": "camel-case", "enabled": true, "value": null}]}
                                        """)));
    }

    @Test
    void formatReturnsFormattedContent() {
        stubFor(post("/format").willReturn(okJson("{\"content\": \"let x = 1;\\n\"}")));

        assertEquals("let x = 1;\n", service.format("let x=1;", "1.0", List.of()));
    }

    @Test
    void executeReturnsOutputs() {
        stubFor(
                post("/execute")
                        .withRequestBody(equalToJson("{\"inputs\": [\"a\"]}", true, true))
                        .willReturn(okJson("{\"outputs\": [\"a\", \"b\"], \"errors\": []}")));

        assertEquals(
                new ExecutionResult(List.of("a", "b"), List.of()),
                service.execute("println(readInput());", "1.0", List.of("a")));
    }

    @Test
    void defaultRulesQueriesByType() {
        stubFor(
                get(urlEqualTo("/rules?type=FORMAT"))
                        .willReturn(
                                okJson(
                                        """
                                        [{"name": "indentation", "description": "Spaces per level",
                                          "enabledByDefault": true, "defaultValue": "4"}]
                                        """)));

        assertEquals(
                List.of(new RuleDefinition("indentation", "Spaces per level", true, "4")),
                service.defaultRules(RuleType.FORMAT));
    }

    @Test
    void serverErrorsAreWrapped() {
        stubFor(post("/validate").willReturn(serverError()));

        LanguageServiceException e =
                assertThrows(LanguageServiceException.class, () -> service.validate("x", "1.0"));
        assertEquals("printscript adapter failed to validate", e.getMessage());
    }

    @Test
    void emptyResponsesAreRejected() {
        stubFor(
                post("/format")
                        .withHeader("Content-Type", equalTo("application/json"))
                        .willReturn(aResponse().withStatus(200)));

        assertThrows(LanguageServiceException.class, () -> service.format("x", "1.0", List.of()));
    }
}
