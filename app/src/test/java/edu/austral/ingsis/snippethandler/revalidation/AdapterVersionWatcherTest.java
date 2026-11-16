package edu.austral.ingsis.snippethandler.revalidation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AdapterVersionWatcherTest {

    @RegisterExtension
    static final WireMockExtension ADAPTER =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final LanguageService printScript = mock(LanguageService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final RevalidationService revalidations = mock(RevalidationService.class);
    private AdapterVersionWatcher watcher;

    @BeforeEach
    void setUp() {
        when(printScript.language()).thenReturn("printscript");
        when(printScript.versions()).thenReturn(Set.of("1.0", "1.9", "1.10"));
        when(redis.opsForValue()).thenReturn(values);
        SnippetProperties properties =
                new SnippetProperties(
                        "printscript",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(
                                "printscript",
                                new SnippetProperties.Language(
                                        ADAPTER.baseUrl() + "/", List.of(), "ps")));
        watcher =
                new AdapterVersionWatcher(
                        new LanguageRegistry(List.of(printScript)),
                        properties,
                        redis,
                        revalidations);
    }

    @Test
    void aNewAdapterRevalidatesAgainstTheNewestVersion() {
        ADAPTER.stubFor(get(urlEqualTo("/version")).willReturn(okJson("{\"version\":\"1.1.2\"}")));
        when(values.getAndSet("adapter-version:printscript", "1.1.2")).thenReturn("1.1.1");

        watcher.check();

        verify(revalidations).start("printscript", "1.10");
    }

    @Test
    void theSameAdapterDoesNothing() {
        ADAPTER.stubFor(get(urlEqualTo("/version")).willReturn(okJson("{\"version\":\"1.1.2\"}")));
        when(values.getAndSet("adapter-version:printscript", "1.1.2")).thenReturn("1.1.2");

        watcher.check();

        verify(revalidations, never()).start(anyString(), anyString());
    }

    @Test
    void anAdapterThatDoesNotAnswerIsCheckedAgainLater() {
        ADAPTER.stubFor(get(urlEqualTo("/version")).willReturn(aResponse().withStatus(503)));

        watcher.check();

        verify(revalidations, never()).start(anyString(), anyString());
        verify(values, never()).getAndSet(anyString(), anyString());
    }

    @Test
    void latestComparesVersionsByNumber() {
        assertThat(AdapterVersionWatcher.latest(printScript)).isEqualTo("1.10");
    }
}
