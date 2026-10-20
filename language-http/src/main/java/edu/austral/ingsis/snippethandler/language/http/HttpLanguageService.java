package edu.austral.ingsis.snippethandler.language.http;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import edu.austral.ingsis.snippethandler.language.ExecutionResult;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleDefinition;
import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.language.Violation;
import edu.austral.ingsis.snippethandler.language.http.AdapterMessages.ExecuteRequest;
import edu.austral.ingsis.snippethandler.language.http.AdapterMessages.FormatRequest;
import edu.austral.ingsis.snippethandler.language.http.AdapterMessages.FormatResponse;
import edu.austral.ingsis.snippethandler.language.http.AdapterMessages.LintRequest;
import edu.austral.ingsis.snippethandler.language.http.AdapterMessages.LintResponse;
import edu.austral.ingsis.snippethandler.language.http.AdapterMessages.ValidateRequest;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link LanguageService} backed by a language adapter service reachable over HTTP. */
public class HttpLanguageService implements LanguageService {

    private final String language;
    private final Set<String> versions;
    private final String fileExtension;
    private final RestClient client;

    /**
     * @param client a {@link RestClient} whose base URL points to the language adapter
     */
    public HttpLanguageService(
            String language, Set<String> versions, String fileExtension, RestClient client) {
        this.language = language;
        this.versions = Set.copyOf(versions);
        this.fileExtension = fileExtension;
        this.client = client;
    }

    @Override
    public String language() {
        return language;
    }

    @Override
    public Set<String> versions() {
        return versions;
    }

    @Override
    public String fileExtension() {
        return fileExtension;
    }

    @Override
    public ValidationResult validate(String source, String version) {
        return call(
                "validate",
                () ->
                        post("/validate", new ValidateRequest(source, version))
                                .body(ValidationResult.class));
    }

    @Override
    public List<Violation> lint(String source, String version, List<RuleConfig> rules) {
        return call(
                        "lint",
                        () ->
                                post("/lint", new LintRequest(source, version, rules))
                                        .body(LintResponse.class))
                .violations();
    }

    @Override
    public String format(String source, String version, List<RuleConfig> rules) {
        return call(
                        "format",
                        () ->
                                post("/format", new FormatRequest(source, version, rules))
                                        .body(FormatResponse.class))
                .content();
    }

    @Override
    public ExecutionResult execute(String source, String version, List<String> inputs) {
        return call(
                "execute",
                () ->
                        post("/execute", new ExecuteRequest(source, version, inputs))
                                .body(ExecutionResult.class));
    }

    @Override
    public List<RuleDefinition> defaultRules(RuleType type) {
        RuleDefinition[] rules =
                call(
                        "list rules",
                        () ->
                                client.get()
                                        .uri("/rules?type={type}", type)
                                        .retrieve()
                                        .body(RuleDefinition[].class));
        return List.of(rules);
    }

    private RestClient.ResponseSpec post(String path, Object body) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve();
    }

    private <T> T call(String operation, Supplier<T> request) {
        T body;
        try {
            body = request.get();
        } catch (RestClientException e) {
            throw new LanguageServiceException(
                    "%s adapter failed to %s".formatted(language, operation), e);
        }
        if (body == null) {
            throw new LanguageServiceException(
                    "%s adapter returned an empty response to %s".formatted(language, operation),
                    null);
        }
        return body;
    }
}
