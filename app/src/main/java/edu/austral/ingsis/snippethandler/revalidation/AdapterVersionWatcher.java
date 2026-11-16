package edu.austral.ingsis.snippethandler.revalidation;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Revalidates the snippets of a language when a new version of its adapter is deployed: nobody
 * triggers it by hand. Every minute it asks each adapter for the version of its engine ({@code GET
 * /version}) and compares it with the last one seen, kept in Redis. {@code getAndSet} is atomic, so
 * with several handler replicas only the one that sees the change first starts the revalidation.
 */
@Component
public class AdapterVersionWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(AdapterVersionWatcher.class);
    static final String KEY_PREFIX = "adapter-version:";

    private final LanguageRegistry languages;
    private final SnippetProperties properties;
    private final StringRedisTemplate redis;
    private final RevalidationService revalidations;
    private final RestClient http = RestClient.create();

    public AdapterVersionWatcher(
            LanguageRegistry languages,
            SnippetProperties properties,
            StringRedisTemplate redis,
            RevalidationService revalidations) {
        this.languages = languages;
        this.properties = properties;
        this.redis = redis;
        this.revalidations = revalidations;
    }

    @Scheduled(
            initialDelayString = "${snippet.revalidation.check-interval:1m}",
            fixedDelayString = "${snippet.revalidation.check-interval:1m}")
    public void check() {
        for (LanguageService language : languages.all()) {
            try {
                check(language);
            } catch (RestClientException e) {
                LOG.warn("Could not read the version of the {} adapter", language.language(), e);
            }
        }
    }

    private void check(LanguageService language) {
        String version = adapterVersion(language.language());
        String previous = redis.opsForValue().getAndSet(KEY_PREFIX + language.language(), version);
        if (Objects.equals(previous, version)) {
            return;
        }
        String target = latest(language);
        LOG.atInfo()
                .addKeyValue("language", language.language())
                .addKeyValue("adapterVersion", version)
                .addKeyValue("previousAdapterVersion", previous)
                .addKeyValue("targetVersion", target)
                .log("New adapter deployed: revalidating its snippets");
        revalidations.start(language.language(), target);
    }

    private String adapterVersion(String language) {
        String url = properties.languages().get(language).url().replaceAll("/+$", "");
        Map<?, ?> body = http.get().uri(url + "/version").retrieve().body(Map.class);
        Object version = body == null ? null : body.get("version");
        if (version == null) {
            throw new RestClientException("The adapter did not report its version");
        }
        return version.toString();
    }

    /** The newest version the language supports, e.g. 1.10 after 1.9. */
    static String latest(LanguageService language) {
        return language.versions().stream()
                .max(
                        Comparator.comparing(
                                version ->
                                        Arrays.stream(version.split("[.]"))
                                                .mapToInt(Integer::parseInt)
                                                .toArray(),
                                Arrays::compare))
                .orElseThrow();
    }
}
