package edu.austral.ingsis.snippethandler.snippet;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.permission.Role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Read side of snippets: viewing, listing and downloading. Never changes anything. */
@Service
public class SnippetQueries {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetQueries.class);

    private final SnippetRepository repository;
    private final SnippetAccess access;
    private final SnippetContents contents;
    private final SnippetStyle style;
    private final LanguageRegistry languages;
    private final PermissionService permissions;

    public SnippetQueries(
            SnippetRepository repository,
            SnippetAccess access,
            SnippetContents contents,
            SnippetStyle style,
            LanguageRegistry languages,
            PermissionService permissions) {
        this.repository = repository;
        this.access = access;
        this.contents = contents;
        this.style = style;
        this.languages = languages;
        this.permissions = permissions;
    }

    /** US 6. */
    public SnippetDetail get(UUID id) {
        Snippet snippet = access.require(id, Role.READER);
        return SnippetDetail.of(snippet, contents.read(id));
    }

    /** US 5: snippets the user can access, filtered and sorted. */
    public Page<SnippetSummary> list(SnippetFilter filter, Pageable pageable) {
        List<UUID> ids = permissions.accessibleSnippets(filter.relation());
        Page<SnippetSummary> page =
                ids.isEmpty()
                        ? Page.empty(pageable)
                        : repository
                                .findAll(SnippetSpecifications.matching(ids, filter), pageable)
                                .map(SnippetSummary::of);
        // "Listed 0" while permissions knows of some, or permissions knowing of none, tells where
        // the snippets of a user went missing.
        LOG.info(
                "snippets listed relation={} accessible={} matching={}",
                filter.relation(),
                ids.size(),
                page.getTotalElements());
        return page;
    }

    /** US 13: original or formatted with the owner's format rules. */
    public SnippetFile download(UUID id, boolean formatted) {
        Snippet snippet = access.require(id, Role.READER);
        LanguageService language = languages.get(snippet.getLanguage());
        String content = contents.read(id);
        if (formatted) {
            content =
                    style.format(
                            snippet.getOwnerId(), language, snippet.getLanguageVersion(), content);
        }
        return new SnippetFile(filename(snippet, language), content);
    }

    private static String filename(Snippet snippet, LanguageService language) {
        String base = snippet.getName().trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return base + "." + language.fileExtension();
    }
}
