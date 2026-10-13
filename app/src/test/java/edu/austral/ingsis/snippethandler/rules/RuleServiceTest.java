package edu.austral.ingsis.snippethandler.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleDefinition;
import edu.austral.ingsis.snippethandler.language.RuleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class RuleServiceTest {

    private static final String USER = "auth0|alice";

    private final RuleOverrideRepository repository = mock(RuleOverrideRepository.class);
    private final LanguageService printScript = mock(LanguageService.class);

    private RuleService service;

    @BeforeEach
    void setUp() {
        when(printScript.language()).thenReturn("printscript");
        when(printScript.defaultRules(RuleType.FORMAT))
                .thenReturn(
                        List.of(
                                new RuleDefinition("indentation", "Spaces", true, "4"),
                                new RuleDefinition("space-around-colon", "Colon", false, null)));
        service =
                new RuleService(
                        repository,
                        new LanguageRegistry(List.of(printScript)),
                        TransactionOperations.withoutTransaction(),
                        new SnippetProperties("printscript", null, null, null, null));
    }

    @Test
    void defaultsApplyWhenUserChangedNothing() {
        assertThat(service.rules(USER, null, RuleType.FORMAT))
                .containsExactly(
                        new RuleView("indentation", "Spaces", true, "4"),
                        new RuleView("space-around-colon", "Colon", false, null));
        assertThat(service.enabledRules(USER, printScript, RuleType.FORMAT))
                .containsExactly(new RuleConfig("indentation", true, "4"));
    }

    @Test
    void overridesWinOverDefaultsButKeepDefaultValue() {
        RuleOverride indentation = new RuleOverride(USER, RuleType.FORMAT, "indentation");
        indentation.configure(false, null);
        RuleOverride colon = new RuleOverride(USER, RuleType.FORMAT, "space-around-colon");
        colon.configure(true, "1");
        when(repository.findAllByUserIdAndType(USER, RuleType.FORMAT))
                .thenReturn(List.of(indentation, colon));

        assertThat(service.rules(USER, "printscript", RuleType.FORMAT))
                .containsExactly(
                        new RuleView("indentation", "Spaces", false, "4"),
                        new RuleView("space-around-colon", "Colon", true, "1"));
    }

    @Test
    void updateStoresOnlyWhatChanged() {
        when(repository.findAllByUserIdAndType(USER, RuleType.FORMAT)).thenReturn(List.of());

        List<RuleView> rules =
                service.update(
                        USER,
                        null,
                        RuleType.FORMAT,
                        List.of(new RuleConfig("indentation", true, "2")));

        assertThat(rules)
                .containsExactly(
                        new RuleView("indentation", "Spaces", true, "2"),
                        new RuleView("space-around-colon", "Colon", false, null));
        verify(repository).save(any());
    }

    @Test
    void rejectsUnknownRules() {
        assertThatThrownBy(
                        () ->
                                service.update(
                                        USER,
                                        null,
                                        RuleType.FORMAT,
                                        List.of(new RuleConfig("tabs", true, null))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown FORMAT rule 'tabs'");
        verify(repository, never()).save(any());
    }
}
