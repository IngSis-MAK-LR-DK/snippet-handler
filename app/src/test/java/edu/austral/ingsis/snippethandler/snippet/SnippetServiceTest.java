package edu.austral.ingsis.snippethandler.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.error.InvalidSnippetException;
import edu.austral.ingsis.snippethandler.events.SnippetEventPublisher;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.SyntaxError;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.language.Violation;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.permission.Role;
import edu.austral.ingsis.snippethandler.rules.RuleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class SnippetServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String OWNER = "auth0|alice";

    private final SnippetRepository repository = mock(SnippetRepository.class);
    private final SnippetAccess access = mock(SnippetAccess.class);
    private final SnippetContents contents = mock(SnippetContents.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final RuleService rules = mock(RuleService.class);
    private final SnippetEventPublisher events = mock(SnippetEventPublisher.class);
    private final LanguageService printScript = mock(LanguageService.class);

    private SnippetService service;

    @BeforeEach
    void setUp() {
        when(printScript.language()).thenReturn("printscript");
        when(printScript.versions()).thenReturn(Set.of("1.0", "1.1"));
        when(printScript.fileExtension()).thenReturn("ps");
        when(printScript.validate(anyString(), anyString())).thenReturn(ValidationResult.ok());
        when(printScript.lint(anyString(), anyString(), anyList())).thenReturn(List.of());
        when(repository.saveAndFlush(any()))
                .thenAnswer(
                        invocation -> {
                            Snippet snippet = invocation.getArgument(0);
                            ReflectionTestUtils.setField(snippet, "id", ID);
                            return snippet;
                        });
        SnippetStyle style = new SnippetStyle(rules);
        service =
                new SnippetService(
                        new SnippetStore(
                                repository, contents, TransactionOperations.withoutTransaction()),
                        access,
                        contents,
                        style,
                        new LanguageRegistry(List.of(printScript)),
                        permissions,
                        events);
    }

    @Test
    void createStoresValidSnippet() {
        SnippetDetail detail =
                service.create(OWNER, new SnippetRequest("Hi", "d", "PrintScript", "1.1", "x;"));

        assertThat(detail.id()).isEqualTo(ID);
        assertThat(detail.language()).isEqualTo("printscript");
        assertThat(detail.compliance()).isEqualTo(Compliance.COMPLIANT);
        verify(contents).write(ID, "x;", null);
        verify(permissions).registerOwner(ID);
    }

    @Test
    void createRejectsInvalidSnippet() {
        SyntaxError error = new SyntaxError("missing-semicolon", "Expected ';'", 1, 2);
        when(printScript.validate("x", "1.1")).thenReturn(ValidationResult.invalid(List.of(error)));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        OWNER,
                                        new SnippetRequest("Hi", null, "printscript", "1.1", "x")))
                .isInstanceOfSatisfying(
                        InvalidSnippetException.class,
                        e -> assertThat(e.getErrors()).containsExactly(error));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(contents, permissions);
    }

    @Test
    void lintFailureDoesNotBlockCreation() {
        when(printScript.lint(anyString(), anyString(), anyList()))
                .thenThrow(new LanguageServiceException("down", null));

        SnippetDetail detail =
                service.create(OWNER, new SnippetRequest("Hi", null, "printscript", "1.1", "x;"));

        assertThat(detail.compliance()).isEqualTo(Compliance.FAILED);
    }

    @Test
    void lintViolationsMakeSnippetNotCompliant() {
        Violation violation = new Violation("camel-case", "Use camelCase", 1, 5);
        when(printScript.lint(anyString(), anyString(), anyList())).thenReturn(List.of(violation));

        SnippetDetail detail =
                service.create(OWNER, new SnippetRequest("Hi", null, "printscript", "1.1", "x;"));

        assertThat(detail.compliance()).isEqualTo(Compliance.NOT_COMPLIANT);
        assertThat(detail.lintViolations()).containsExactly(violation);
    }

    @Test
    void metadataOnlyUpdateSkipsValidationAndTests() {
        Snippet snippet = existing();
        when(access.require(ID, Role.OWNER)).thenReturn(snippet);
        when(contents.read(ID)).thenReturn("x;");

        SnippetDetail detail =
                service.update(ID, new SnippetUpdateRequest("New", null, null, null, null));

        assertThat(detail.name()).isEqualTo("New");
        assertThat(detail.description()).isEqualTo("desc");
        verify(printScript, never()).validate(anyString(), anyString());
        verify(contents, never()).write(any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void contentUpdateValidatesStoresAndRunsTests() {
        when(access.require(ID, Role.OWNER)).thenReturn(existing());
        when(contents.read(ID)).thenReturn("x;");

        service.update(ID, new SnippetUpdateRequest(null, null, null, null, "y;"));

        verify(printScript).validate("y;", "1.0");
        verify(contents).write(ID, "y;", "x;");
        verify(events).snippetUpdated(ID);
    }

    @Test
    void versionChangeRevalidatesSameContent() {
        when(access.require(ID, Role.OWNER)).thenReturn(existing());
        when(contents.read(ID)).thenReturn("x;");

        SnippetDetail detail =
                service.update(ID, new SnippetUpdateRequest(null, null, null, "1.1", null));

        assertThat(detail.version()).isEqualTo("1.1");
        verify(printScript).validate("x;", "1.1");
        verify(contents, never()).write(any(), any(), any());
        verify(events).snippetUpdated(ID);
    }

    @Test
    void invalidUpdateKeepsSnippetUnchanged() {
        when(access.require(ID, Role.OWNER)).thenReturn(existing());
        when(contents.read(ID)).thenReturn("x;");
        when(printScript.validate("bad", "1.0"))
                .thenReturn(ValidationResult.invalid(List.of(new SyntaxError("r", "m", 1, 1))));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        ID,
                                        new SnippetUpdateRequest(null, null, null, null, "bad")))
                .isInstanceOf(InvalidSnippetException.class);
        verify(contents, never()).write(any(), any(), any());
    }

    @Test
    void cannotShareWithOwner() {
        when(access.require(ID, Role.OWNER)).thenReturn(existing());

        assertThatThrownBy(() -> service.share(ID, OWNER)).isInstanceOf(BadRequestException.class);
        verify(permissions, never()).share(any(), any());
    }

    @Test
    void formatWithoutChangesDoesNotWrite() {
        when(access.require(ID, Role.OWNER)).thenReturn(existing());
        when(contents.read(ID)).thenReturn("x;");
        when(printScript.format(eq("x;"), eq("1.0"), anyList())).thenReturn("x;");

        assertThat(service.format(ID).content()).isEqualTo("x;");
        verify(contents, never()).write(any(), any(), any());
    }

    @Test
    void formatStoresFormattedContentAndRelints() {
        when(access.require(ID, Role.OWNER)).thenReturn(existing());
        when(contents.read(ID)).thenReturn("x;");
        when(printScript.format(eq("x;"), eq("1.0"), anyList())).thenReturn("x ;");

        assertThat(service.format(ID).content()).isEqualTo("x ;");
        verify(printScript).lint(eq("x ;"), eq("1.0"), anyList());
        verify(contents).write(ID, "x ;", "x;");
    }

    @Test
    void deleteRemovesRowPermissionsAndContent() {
        service.delete(ID);

        verify(access).require(ID, Role.OWNER);
        verify(repository).deleteById(ID);
        verify(permissions).removeSnippet(ID);
        verify(contents).deleteAfterCommit(ID);
    }

    @Test
    void sharesWithAnotherUser() {
        when(access.require(ID, Role.OWNER)).thenReturn(existing());

        service.share(ID, "auth0|bob");

        verify(permissions).share(ID, "auth0|bob");
    }

    private static Snippet existing() {
        Snippet snippet = new Snippet("My snippet", "desc", "printscript", "1.0", OWNER);
        ReflectionTestUtils.setField(snippet, "id", ID);
        return snippet;
    }
}
