package edu.austral.ingsis.snippethandler.snippet;

import java.util.Objects;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.error.InvalidSnippetException;
import edu.austral.ingsis.snippethandler.events.SnippetEventPublisher;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.SnippetValidator;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.languagecatalog.LanguageResolver;
import edu.austral.ingsis.snippethandler.languagecatalog.LanguageSelection;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.permission.Role;

import org.springframework.stereotype.Service;

/**
 * Write side of snippets. Each operation first does everything that talks to the language adapter
 * (validate, lint, format), which can be slow, and only then hands the result to {@link
 * SnippetStore}, so no database transaction is held open while waiting on it.
 */
@Service
public class SnippetService {

    private final SnippetStore store;
    private final SnippetAccess access;
    private final SnippetContents contents;
    private final SnippetStyle style;
    private final LanguageResolver languages;
    private final PermissionService permissions;
    private final SnippetEventPublisher events;

    public SnippetService(
            SnippetStore store,
            SnippetAccess access,
            SnippetContents contents,
            SnippetStyle style,
            LanguageResolver languages,
            PermissionService permissions,
            SnippetEventPublisher events) {
        this.store = store;
        this.access = access;
        this.contents = contents;
        this.style = style;
        this.languages = languages;
        this.permissions = permissions;
        this.events = events;
    }

    /**
     * US 1 and 3: only snippets valid for their language are accepted. Without a version, the
     * latest one of the language is used.
     */
    public SnippetDetail create(String userId, SnippetRequest request) {
        LanguageSelection selection = languages.select(request.language(), request.version());
        LanguageService language = selection.language();
        String version = selection.version();
        String content = request.content();
        requireValid(language, content, version);
        Snippet snippet =
                new Snippet(
                        request.name(),
                        request.description(),
                        language.language(),
                        version,
                        userId);
        snippet.linted(style.lint(userId, language, version, content));

        Snippet saved =
                store.save(snippet, content, null, s -> permissions.registerOwner(s.getId()));
        return SnippetDetail.of(saved, content);
    }

    /** US 2 and 4: any field can change, the result must still be valid for its language. */
    public SnippetDetail update(UUID id, SnippetUpdateRequest request) {
        Snippet snippet = access.require(id, Role.OWNER);
        String current = contents.read(id);
        String content = Objects.requireNonNullElse(request.content(), current);
        String version =
                Objects.requireNonNullElse(request.version(), snippet.getLanguageVersion());
        LanguageService language =
                languages
                        .select(
                                Objects.requireNonNullElse(
                                        request.language(), snippet.getLanguage()),
                                version)
                        .language();
        boolean sourceChanged =
                !content.equals(current)
                        || !language.language().equals(snippet.getLanguage())
                        || !version.equals(snippet.getLanguageVersion());

        if (sourceChanged) {
            requireValid(language, content, version);
            snippet.linted(style.lint(snippet.getOwnerId(), language, version, content));
        }
        snippet.rename(
                Objects.requireNonNullElse(request.name(), snippet.getName()),
                Objects.requireNonNullElse(request.description(), snippet.getDescription()));
        snippet.changeLanguage(language.language(), version);

        Snippet saved =
                store.save(
                        snippet,
                        content,
                        current,
                        s -> {
                            // US 16: published within the transaction, delivered after commit.
                            if (sourceChanged) {
                                events.snippetUpdated(id);
                            }
                        });
        return SnippetDetail.of(saved, content);
    }

    public void delete(UUID id) {
        access.require(id, Role.OWNER);
        store.delete(id, () -> permissions.removeSnippet(id));
    }

    /** US 7: the target user gets read access over the snippet and its tests. */
    public void share(UUID id, String userId) {
        Snippet snippet = access.require(id, Role.OWNER);
        if (snippet.getOwnerId().equals(userId)) {
            throw new BadRequestException("A snippet cannot be shared with its owner");
        }
        permissions.share(id, userId);
    }

    /** Applies the owner's format rules and stores the result. */
    public SnippetDetail format(UUID id) {
        Snippet snippet = access.require(id, Role.OWNER);
        LanguageService language = languages.language(snippet.getLanguage());
        String version = snippet.getLanguageVersion();
        String current = contents.read(id);
        String formatted = style.format(snippet.getOwnerId(), language, version, current);
        if (formatted.equals(current)) {
            return SnippetDetail.of(snippet, current);
        }
        snippet.linted(style.lint(snippet.getOwnerId(), language, version, formatted));
        return SnippetDetail.of(store.save(snippet, formatted, current, s -> {}), formatted);
    }

    /**
     * Formats the editor's content with the caller's format rules; nothing is stored. Invalid
     * content is rejected like on create, with the broken rule and its position.
     */
    public String formatPreview(String userId, FormatPreviewRequest request) {
        LanguageSelection selection = languages.select(request.language(), request.version());
        requireValid(selection.language(), request.content(), selection.version());
        return style.format(userId, selection.language(), selection.version(), request.content());
    }

    private static void requireValid(SnippetValidator validator, String content, String version) {
        ValidationResult result = validator.validate(content, version);
        if (!result.valid()) {
            throw new InvalidSnippetException(result.errors());
        }
    }
}
