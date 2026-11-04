package edu.austral.ingsis.snippethandler.revalidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.UnsupportedLanguageException;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RevalidationServiceTest {

    private final LanguageRegistry languages = mock(LanguageRegistry.class);
    private final SnippetRepository snippets = mock(SnippetRepository.class);
    private final RevalidationRunRepository runs = mock(RevalidationRunRepository.class);
    private final RevalidationWorker worker = mock(RevalidationWorker.class);
    private final RevalidationService service =
            new RevalidationService(languages, snippets, runs, worker);

    @Test
    void startCreatesARunForEverySnippetOfTheLanguageAndLaunchesIt() {
        LanguageService language = mock(LanguageService.class);
        when(languages.get("printscript", "1.2")).thenReturn(language);
        when(language.language()).thenReturn("printscript");
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(snippets.findIdsByLanguage("printscript")).thenReturn(ids);
        RevalidationRun saved = new RevalidationRun("printscript", "1.2", ids.size());
        UUID runId = UUID.randomUUID();
        ReflectionTestUtils.setField(saved, "id", runId);
        when(runs.save(any())).thenReturn(saved);

        RevalidationRunView view = service.start("printscript", "1.2");

        assertEquals(runId, view.id());
        assertEquals(2, view.total());
        verify(worker).revalidate(runId, ids, "printscript", "1.2");
    }

    @Test
    void startRejectsAVersionTheLanguageDoesNotSupport() {
        when(languages.get(anyString(), anyString()))
                .thenThrow(new UnsupportedLanguageException("printscript", "9.9"));

        assertThrows(UnsupportedLanguageException.class, () -> service.start("printscript", "9.9"));
        verify(runs, never()).save(any());
    }
}
