package edu.austral.ingsis.snippethandler.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.permission.Relation;
import edu.austral.ingsis.snippethandler.permission.Role;
import edu.austral.ingsis.snippethandler.rules.RuleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class SnippetQueriesTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String OWNER = "auth0|alice";

    private final SnippetRepository repository = mock(SnippetRepository.class);
    private final SnippetAccess access = mock(SnippetAccess.class);
    private final SnippetContents contents = mock(SnippetContents.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final RuleService rules = mock(RuleService.class);
    private final LanguageService printScript = mock(LanguageService.class);

    private SnippetQueries queries;

    @BeforeEach
    void setUp() {
        when(printScript.language()).thenReturn("printscript");
        when(printScript.fileExtension()).thenReturn("ps");
        when(access.require(ID, Role.READER))
                .thenReturn(new Snippet("My snippet", "desc", "printscript", "1.0", OWNER));
        when(contents.read(ID)).thenReturn("x;");
        queries =
                new SnippetQueries(
                        repository,
                        access,
                        contents,
                        new SnippetStyle(rules),
                        new LanguageRegistry(List.of(printScript)),
                        permissions);
    }

    @Test
    void getReturnsMetadataAndContent() {
        SnippetDetail detail = queries.get(ID);

        assertThat(detail.name()).isEqualTo("My snippet");
        assertThat(detail.content()).isEqualTo("x;");
    }

    @Test
    void listWithoutAccessibleSnippetsSkipsQuery() {
        when(permissions.accessibleSnippets(Relation.SHARED)).thenReturn(List.of());

        Page<SnippetSummary> page =
                queries.list(
                        new SnippetFilter(Relation.SHARED, null, null, null),
                        PageRequest.of(0, 10));

        assertThat(page).isEmpty();
        verify(repository, never())
                .findAll(ArgumentMatchers.<Specification<Snippet>>any(), any(Pageable.class));
    }

    @Test
    void downloadUsesOwnerFormatRules() {
        List<RuleConfig> ownerRules = List.of(new RuleConfig("indentation", true, "2"));
        when(rules.enabledRules(OWNER, printScript, RuleType.FORMAT)).thenReturn(ownerRules);
        when(printScript.format("x;", "1.0", ownerRules)).thenReturn("x ;");

        assertThat(queries.download(ID, true)).isEqualTo(new SnippetFile("My_snippet.ps", "x ;"));
        assertThat(queries.download(ID, false)).isEqualTo(new SnippetFile("My_snippet.ps", "x;"));
    }
}
