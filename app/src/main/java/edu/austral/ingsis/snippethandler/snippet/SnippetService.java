package edu.austral.ingsis.snippethandler.snippet;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.error.InvalidSnippetException;
import edu.austral.ingsis.snippethandler.events.SnippetEventPublisher;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.permission.Permission;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.rules.RuleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnippetService {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetService.class);

    private final SnippetRepository repository;
    private final SnippetAccess access;
    private final SnippetContents contents;
    private final LanguageRegistry languages;
    private final PermissionService permissions;
    private final RuleService rules;
    private final SnippetEventPublisher events;

    public SnippetService(
            SnippetRepository repository,
            SnippetAccess access,
            SnippetContents contents,
            LanguageRegistry languages,
            PermissionService permissions,
            RuleService rules,
            SnippetEventPublisher events) {
        this.repository = repository;
        this.access = access;
        this.contents = contents;
        this.languages = languages;
        this.permissions = permissions;
        this.rules = rules;
        this.events = events;
    }

    /** US 1 and 3: only snippets valid for their language are accepted. */
    @Transactional
    public SnippetDetail create(String userId, SnippetRequest request) {
        LanguageService language = languages.get(request.language(), request.version());
        requireValid(language, request.content(), request.version());
        Snippet snippet =
                repository.save(
                        new Snippet(
                                request.name(),
                                request.description(),
                                language.language(),
                                request.version(),
                                userId));
        lint(snippet, language, request.content());
        contents.write(snippet.getId(), request.content(), null);
        permissions.registerOwner(snippet.getId());
        return SnippetDetail.of(snippet, request.content());
    }

    /** US 2 and 4: any field can change, the result must still be valid for its language. */
    @Transactional
    public SnippetDetail update(UUID id, SnippetUpdateRequest request) {
        Snippet snippet = access.require(id, Permission.WRITE);
        String current = contents.read(id);
        String content = Objects.requireNonNullElse(request.content(), current);
        String version =
                Objects.requireNonNullElse(request.version(), snippet.getLanguageVersion());
        LanguageService language =
                languages.get(
                        Objects.requireNonNullElse(request.language(), snippet.getLanguage()),
                        version);
        boolean sourceChanged =
                !content.equals(current)
                        || !language.language().equals(snippet.getLanguage())
                        || !version.equals(snippet.getLanguageVersion());

        if (sourceChanged) {
            requireValid(language, content, version);
        }
        snippet.rename(
                Objects.requireNonNullElse(request.name(), snippet.getName()),
                Objects.requireNonNullElse(request.description(), snippet.getDescription()));
        snippet.changeLanguage(language.language(), version);
        if (sourceChanged) {
            lint(snippet, language, content);
        }
        if (!content.equals(current)) {
            contents.write(id, content, current);
        }
        if (sourceChanged) {
            events.snippetUpdated(id);
        }
        return SnippetDetail.of(repository.save(snippet), content);
    }

    /** US 6. */
    @Transactional(readOnly = true)
    public SnippetDetail get(UUID id) {
        Snippet snippet = access.require(id, Permission.READ);
        return SnippetDetail.of(snippet, contents.read(id));
    }

    /** US 5: snippets the user can access, filtered and sorted. */
    @Transactional(readOnly = true)
    public Page<SnippetSummary> list(SnippetFilter filter, Pageable pageable) {
        List<UUID> ids = permissions.accessibleSnippets(filter.relation());
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        return repository
                .findAll(SnippetSpecifications.matching(ids, filter), pageable)
                .map(SnippetSummary::of);
    }

    @Transactional
    public void delete(UUID id) {
        Snippet snippet = access.require(id, Permission.OWNER);
        repository.delete(snippet);
        permissions.removeSnippet(id);
        contents.deleteAfterCommit(id);
    }

    /** US 7: the target user gets read access over the snippet and its tests. */
    @Transactional(readOnly = true)
    public void share(UUID id, String userId) {
        Snippet snippet = access.require(id, Permission.OWNER);
        if (snippet.getOwnerId().equals(userId)) {
            throw new BadRequestException("A snippet cannot be shared with its owner");
        }
        permissions.share(id, userId);
    }

    /** US 13: original or formatted with the owner's format rules. */
    @Transactional(readOnly = true)
    public SnippetFile download(UUID id, boolean formatted) {
        Snippet snippet = access.require(id, Permission.READ);
        LanguageService language = languages.get(snippet.getLanguage());
        String content = contents.read(id);
        if (formatted) {
            content = format(snippet, language, content);
        }
        return new SnippetFile(filename(snippet, language), content);
    }

    /** Applies the owner's format rules and stores the result. */
    @Transactional
    public SnippetDetail format(UUID id) {
        Snippet snippet = access.require(id, Permission.WRITE);
        LanguageService language = languages.get(snippet.getLanguage());
        String current = contents.read(id);
        String formatted = format(snippet, language, current);
        if (!formatted.equals(current)) {
            lint(snippet, language, formatted);
            contents.write(id, formatted, current);
            repository.save(snippet);
        }
        return SnippetDetail.of(snippet, formatted);
    }

    private static void requireValid(LanguageService language, String content, String version) {
        ValidationResult result = language.validate(content, version);
        if (!result.valid()) {
            throw new InvalidSnippetException(result.errors());
        }
    }

    private void lint(Snippet snippet, LanguageService language, String content) {
        try {
            snippet.lintPassed(
                    language.lint(
                            content,
                            snippet.getLanguageVersion(),
                            rules.enabledRules(snippet.getOwnerId(), language, RuleType.LINT)));
        } catch (LanguageServiceException e) {
            LOG.warn("Could not lint snippet {}", snippet.getId(), e);
            snippet.lintFailed();
        }
    }

    private String format(Snippet snippet, LanguageService language, String content) {
        return language.format(
                content,
                snippet.getLanguageVersion(),
                rules.enabledRules(snippet.getOwnerId(), language, RuleType.FORMAT));
    }

    private static String filename(Snippet snippet, LanguageService language) {
        String base = snippet.getName().trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return base + "." + language.fileExtension();
    }
}
