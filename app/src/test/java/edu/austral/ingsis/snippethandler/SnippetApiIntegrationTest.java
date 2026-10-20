package edu.austral.ingsis.snippethandler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.support.InMemoryAssetStore;
import edu.austral.ingsis.snippethandler.testcase.TestCaseRepository;
import edu.austral.ingsis.snippethandler.testcase.TestStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end through the HTTP API with a real Postgres; the language adapter and the permissions
 * service are WireMock stubs, the bucket is in memory.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SnippetApiIntegrationTest {

    private static final String ALICE = "auth0|alice";
    private static final String SNIPPET_PERMISSION = "/permissions/[0-9a-f-]{36}";
    private static final String VALID_SOURCE =
            "let greeting: string = \"hi\";\nprintln(greeting);\n";

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static final WireMockExtension SERVICES =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://test.auth0.local/");
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.audiences", () -> "snippet-searcher");
        registry.add("snippet.permission.url", SERVICES::baseUrl);
        registry.add("snippet.languages.printscript.url", SERVICES::baseUrl);
    }

    @TestConfiguration
    static class Assets {
        @Bean
        @Primary
        InMemoryAssetStore inMemoryAssetStore() {
            return new InMemoryAssetStore();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private InMemoryAssetStore assets;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestCaseRepository testCases;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE snippet, test_case, rule_config CASCADE");
        assets.clear();
        SERVICES.resetAll();

        SERVICES.stubFor(post(urlEqualTo("/permissions")).willReturn(aResponse().withStatus(201)));
        SERVICES.stubFor(
                get(urlPathMatching(SNIPPET_PERMISSION))
                        .willReturn(okJson("{\"role\": \"OWNER\"}")));
        SERVICES.stubFor(
                post(urlPathMatching(SNIPPET_PERMISSION + "/share"))
                        .willReturn(aResponse().withStatus(204)));
        SERVICES.stubFor(
                delete(urlPathMatching(SNIPPET_PERMISSION))
                        .willReturn(aResponse().withStatus(204)));

        SERVICES.stubFor(
                get(urlEqualTo("/rules?type=LINT"))
                        .willReturn(
                                okJson(
                                        """
                [{"name": "camel-case", "description": "Identifiers in camelCase",
                  "enabledByDefault": true, "defaultValue": null}]
                """)));
        SERVICES.stubFor(
                get(urlEqualTo("/rules?type=FORMAT"))
                        .willReturn(
                                okJson(
                                        """
                [{"name": "indentation", "description": "Spaces per level",
                  "enabledByDefault": true, "defaultValue": "4"}]
                """)));
        SERVICES.stubFor(post("/validate").willReturn(okJson("{\"valid\": true, \"errors\": []}")));
        SERVICES.stubFor(
                post("/validate")
                        .withRequestBody(matchingJsonPath("$.content", containing("INVALID")))
                        .willReturn(
                                okJson(
                                        """
                        {"valid": false, "errors": [{"rule": "missing-semicolon",
                          "message": "Expected ';'", "line": 3, "column": 7}]}
                        """)));
        SERVICES.stubFor(post("/lint").willReturn(okJson("{\"violations\": []}")));
        SERVICES.stubFor(
                post("/lint")
                        .withRequestBody(matchingJsonPath("$.content", containing("snake_case")))
                        .willReturn(
                                okJson(
                                        """
                        {"violations": [{"rule": "camel-case", "message": "Use camelCase",
                          "line": 1, "column": 5}]}
                        """)));
        SERVICES.stubFor(post("/format").willReturn(okJson("{\"content\": \"formatted;\\n\"}")));
        SERVICES.stubFor(
                post("/execute").willReturn(okJson("{\"outputs\": [\"hi\"], \"errors\": []}")));
    }

    @Test
    void healthIsPublicButSnippetsRequireToken() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/snippets")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsLanguages() throws Exception {
        mvc.perform(as(MockMvcRequestBuilders.get("/languages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("printscript"))
                .andExpect(jsonPath("$[0].versions[0]").value("1.0"))
                .andExpect(jsonPath("$[0].fileExtension").value("ps"));
    }

    @Test
    void createsSnippetFromFile() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "hello.ps", "text/plain", VALID_SOURCE.getBytes());

        String body =
                mvc.perform(
                                as(
                                        multipart("/snippets")
                                                .file(file)
                                                .param("name", "Hello")
                                                .param("description", "Greets")
                                                .param("language", "printscript")
                                                .param("version", "1.1")))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Location", containsString("/snippets/")))
                        .andExpect(jsonPath("$.ownerId").value(ALICE))
                        .andExpect(jsonPath("$.compliance").value("COMPLIANT"))
                        .andExpect(jsonPath("$.content").value(VALID_SOURCE))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String id = json.readTree(body).get("id").asText();
        assertThat(assets.get(id)).contains(VALID_SOURCE);
        SERVICES.verify(
                postRequestedFor(urlEqualTo("/permissions"))
                        .withHeader("Authorization", equalTo("Bearer token"))
                        .withRequestBody(equalToJson("{\"snippetId\": \"" + id + "\"}")));
    }

    @Test
    void rejectsInvalidSnippetReportingRuleAndPosition() throws Exception {
        mvc.perform(as(postJson("/snippets", snippetJson("Broken", "let x = 1\nINVALID"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].rule").value("missing-semicolon"))
                .andExpect(jsonPath("$.errors[0].line").value(3))
                .andExpect(jsonPath("$.errors[0].column").value(7));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM snippet", Integer.class)).isZero();
    }

    @Test
    void rejectsUnsupportedLanguageAndMissingFields() throws Exception {
        mvc.perform(
                        as(
                                postJson(
                                        "/snippets",
                                        """
                        {"name": "x", "language": "cobol", "version": "1", "content": "x"}
                        """)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(postJson("/snippets", "{\"name\": \"x\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void storesLintViolations() throws Exception {
        mvc.perform(as(postJson("/snippets", snippetJson("Snake", "let snake_case = 1;"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compliance").value("NOT_COMPLIANT"))
                .andExpect(jsonPath("$.lintViolations[0].rule").value("camel-case"));
    }

    @Test
    void undoesEverythingWhenPermissionsServiceFails() throws Exception {
        SERVICES.stubFor(post(urlEqualTo("/permissions")).willReturn(serverError()));

        mvc.perform(as(postJson("/snippets", snippetJson("Hello", VALID_SOURCE))))
                .andExpect(status().isBadGateway());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM snippet", Integer.class)).isZero();
        assertThat(assets.size()).isZero();
    }

    @Test
    void readersCanReadButNotModify() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);

        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hello"))
                .andExpect(jsonPath("$.content").value(VALID_SOURCE));

        SERVICES.stubFor(
                get(urlEqualTo("/permissions/" + id)).willReturn(okJson("{\"role\": \"READER\"}")));
        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + id))).andExpect(status().isOk());
        mvc.perform(as(MockMvcRequestBuilders.delete("/snippets/" + id)))
                .andExpect(status().isForbidden());

        SERVICES.stubFor(
                get(urlEqualTo("/permissions/" + id)).willReturn(okJson("{\"role\": \"NONE\"}")));
        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownSnippetIsNotFound() throws Exception {
        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesSnippetAndRunsItsTests() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);
        String testId = createTest(id);

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.put("/snippets/" + id)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\": \"Renamed\", \"content\": \"println(\\\"hi\\\");\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.description").value("A snippet"))
                .andExpect(jsonPath("$.content").value("println(\"hi\");"));

        assertThat(assets.get(id)).contains("println(\"hi\");");
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                testCases
                                                        .findById(UUID.fromString(testId))
                                                        .orElseThrow()
                                                        .getLastStatus())
                                        .isEqualTo(TestStatus.PASSED));
    }

    @Test
    void updatesSnippetFromFileAndRejectsInvalidChanges() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);
        MockMultipartFile file =
                new MockMultipartFile("file", "hello.ps", "text/plain", "println(2);".getBytes());

        mvc.perform(
                        as(
                                multipart("/snippets/" + id)
                                        .file(file)
                                        .param("description", "New")
                                        .with(
                                                request -> {
                                                    request.setMethod("PUT");
                                                    return request;
                                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("New"))
                .andExpect(jsonPath("$.content").value("println(2);"));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.put("/snippets/" + id)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"content\": \"INVALID\"}")))
                .andExpect(status().isUnprocessableEntity());
        assertThat(assets.get(id)).contains("println(2);");
    }

    @Test
    void listsAccessibleSnippetsWithFiltersAndSorting() throws Exception {
        String hello = createSnippet("Hello", VALID_SOURCE);
        String snake = createSnippet("Another", "let snake_case = 1;");
        createSnippet("Hidden", VALID_SOURCE);
        SERVICES.stubFor(
                get(urlEqualTo("/permissions/snippets?relation=all"))
                        .willReturn(
                                okJson(
                                        """
                                        [{"snippetId": "%s", "role": "OWNER"},
                                         {"snippetId": "%s", "role": "READER"}]
                                        """
                                                .formatted(hello, snake))));
        SERVICES.stubFor(
                get(urlEqualTo("/permissions/snippets?relation=shared")).willReturn(okJson("[]")));

        mvc.perform(as(MockMvcRequestBuilders.get("/snippets").param("sort", "name,desc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Hello"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.get("/snippets")
                                        .param("compliance", "NOT_COMPLIANT")
                                        .param("language", "PrintScript")
                                        .param("name", "anoth")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(snake));

        mvc.perform(as(MockMvcRequestBuilders.get("/snippets").param("relation", "SHARED")))
                .andExpect(jsonPath("$.content", hasSize(0)));

        mvc.perform(as(MockMvcRequestBuilders.get("/snippets").param("sort", "nope")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sharesSnippetWithAnotherUser() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);

        mvc.perform(as(postJson("/snippets/" + id + "/share", "{\"userId\": \"auth0|bob\"}")))
                .andExpect(status().isNoContent());
        SERVICES.verify(
                postRequestedFor(urlEqualTo("/permissions/" + id + "/share"))
                        .withRequestBody(equalToJson("{\"userId\": \"auth0|bob\"}")));

        mvc.perform(as(postJson("/snippets/" + id + "/share", "{\"userId\": \"" + ALICE + "\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadsOriginalAndFormattedVersions() throws Exception {
        String id = createSnippet("My snippet", VALID_SOURCE);

        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + id + "/download")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("My_snippet.ps")))
                .andExpect(body(VALID_SOURCE));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.get("/snippets/" + id + "/download")
                                        .param("formatted", "true")))
                .andExpect(body("formatted;\n"));
        SERVICES.verify(
                postRequestedFor(urlEqualTo("/format"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"rules\": [{\"name\": \"indentation\", \"enabled\": true, \"value\": \"4\"}]}",
                                        true,
                                        true)));
    }

    @Test
    void formatsAndStoresSnippet() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);

        mvc.perform(as(MockMvcRequestBuilders.post("/snippets/" + id + "/format")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("formatted;\n"));
        assertThat(assets.get(id)).contains("formatted;\n");
    }

    @Test
    void managesAndRunsTests() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);
        String testId = createTest(id);

        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + id + "/tests")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastStatus").value("PENDING"));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.post(
                                        "/snippets/" + id + "/tests/" + testId + "/run")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.outputs[0]").value("hi"));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.put("/snippets/" + id + "/tests/" + testId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\": \"bye\", \"inputs\": [], \"expectedOutputs\": [\"bye\"]}")))
                .andExpect(jsonPath("$.lastStatus").value("PENDING"));
        mvc.perform(
                        as(
                                MockMvcRequestBuilders.post(
                                        "/snippets/" + id + "/tests/" + testId + "/run")))
                .andExpect(jsonPath("$.status").value("FAILED"));

        mvc.perform(as(MockMvcRequestBuilders.delete("/snippets/" + id + "/tests/" + testId)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        as(
                                MockMvcRequestBuilders.post(
                                        "/snippets/" + id + "/tests/" + testId + "/run")))
                .andExpect(status().isNotFound());
    }

    @Test
    void configuresRules() throws Exception {
        mvc.perform(as(MockMvcRequestBuilders.get("/rules/lint")))
                .andExpect(jsonPath("$[0].name").value("camel-case"))
                .andExpect(jsonPath("$[0].enabled").value(true));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.put("/rules/lint")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "[{\"name\": \"camel-case\", \"enabled\": false}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enabled").value(false));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.put("/rules/format")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "[{\"name\": \"indentation\", \"enabled\": true, \"value\": \"2\"}]")))
                .andExpect(jsonPath("$[0].value").value("2"));
        mvc.perform(
                        as(
                                MockMvcRequestBuilders.get("/rules/format")
                                        .param("language", "printscript")))
                .andExpect(jsonPath("$[0].value").value("2"));

        mvc.perform(
                        as(
                                MockMvcRequestBuilders.put("/rules/format")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"name\": \"unknown\", \"enabled\": true}]")))
                .andExpect(status().isBadRequest());

        String id = createSnippet("Hello", "let snake_case = 1;");
        SERVICES.verify(
                postRequestedFor(urlEqualTo("/lint"))
                        .withRequestBody(equalToJson("{\"rules\": []}", true, true)));
        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + id)))
                .andExpect(jsonPath("$.compliance").value("NOT_COMPLIANT"));
    }

    @Test
    void deletesSnippetWithItsTestsAndContent() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);
        createTest(id);

        mvc.perform(as(MockMvcRequestBuilders.delete("/snippets/" + id)))
                .andExpect(status().isNoContent());

        assertThat(assets.contains(id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_case", Integer.class)).isZero();
        mvc.perform(as(MockMvcRequestBuilders.get("/snippets/" + id)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsSnippetWithLatestVersionWhenMissing() throws Exception {
        mvc.perform(
                        as(
                                postJson(
                                        "/snippets",
                                        """
                                        {"name": "No version", "language": "printscript",
                                         "content": "println(1);"}
                                        """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("1.1"));
    }

    @Test
    void formatsEditorContentWithoutStoringIt() throws Exception {
        mvc.perform(as(postJson("/snippets/format", "{\"content\": \"let x=1;\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("formatted;\n"));
        SERVICES.verify(
                postRequestedFor(urlEqualTo("/format"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"content\": \"let x=1;\", \"version\": \"1.1\"}",
                                        true,
                                        true)));
        assertThat(assets.size()).isZero();

        mvc.perform(as(postJson("/snippets/format", "{\"content\": \"INVALID\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].rule").value("missing-semicolon"));
    }

    @Test
    void runsTestFromThePanelWithoutStoringIt() throws Exception {
        String id = createSnippet("Hello", VALID_SOURCE);

        mvc.perform(
                        as(
                                postJson(
                                        "/snippets/" + id + "/tests/run",
                                        "{\"inputs\": [], \"expectedOutputs\": [\"hi\"]}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.testId").value(nullValue()));
        mvc.perform(
                        as(
                                postJson(
                                        "/snippets/" + id + "/tests/run",
                                        "{\"inputs\": [], \"expectedOutputs\": [\"bye\"]}")))
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_case", Integer.class)).isZero();

        SERVICES.stubFor(
                get(urlEqualTo("/permissions/" + id)).willReturn(okJson("{\"role\": \"NONE\"}")));
        mvc.perform(
                        as(
                                postJson(
                                        "/snippets/" + id + "/tests/run",
                                        "{\"inputs\": [], \"expectedOutputs\": []}")))
                .andExpect(status().isForbidden());
    }

    private String createSnippet(String name, String content) throws Exception {
        String body =
                mvc.perform(as(postJson("/snippets", snippetJson(name, content))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private String createTest(String snippetId) throws Exception {
        String body =
                mvc.perform(
                                as(
                                        postJson(
                                                "/snippets/" + snippetId + "/tests",
                                                "{\"name\": \"greets\", \"inputs\": [\"x\"], \"expectedOutputs\": [\"hi\"]}")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private String snippetJson(String name, String content) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "name", name,
                        "description", "A snippet",
                        "language", "printscript",
                        "version", "1.1",
                        "content", content));
    }

    private static MockHttpServletRequestBuilder postJson(String path, String body) {
        return MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.with(alice());
    }

    private static JwtRequestPostProcessor alice() {
        return jwt().jwt(token -> token.subject(ALICE));
    }

    private static ResultMatcher body(String expected) {
        return content().string(expected);
    }
}
