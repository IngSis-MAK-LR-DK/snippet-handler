package edu.austral.ingsis.snippethandler.languagecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.UnsupportedLanguageException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LanguageResolverTest {

    private final LanguageService printScript = mock(LanguageService.class);
    private LanguageResolver resolver;

    @BeforeEach
    void setUp() {
        when(printScript.language()).thenReturn("printscript");
        when(printScript.versions()).thenReturn(Set.of("1.0", "1.1"));
        when(printScript.latestVersion()).thenReturn("1.1");
        resolver =
                new LanguageResolver(
                        new LanguageRegistry(List.of(printScript)),
                        new SnippetProperties("printscript", null, null, null, null));
    }

    @Test
    void blankLanguageMeansDefault() {
        assertThat(resolver.language(null)).isSameAs(printScript);
        assertThat(resolver.language(" ")).isSameAs(printScript);
    }

    @Test
    void missingVersionMeansLatest() {
        assertThat(resolver.select("printscript", null))
                .isEqualTo(new LanguageSelection(printScript, "1.1"));
        assertThat(resolver.select(null, "1.0"))
                .isEqualTo(new LanguageSelection(printScript, "1.0"));
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThatThrownBy(() -> resolver.select("printscript", "9.9"))
                .isInstanceOf(UnsupportedLanguageException.class);
    }
}
