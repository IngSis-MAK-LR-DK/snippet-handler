package edu.austral.ingsis.snippethandler.snippet;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.ForbiddenException;
import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.permission.PermissionService;
import edu.austral.ingsis.snippethandler.permission.Role;

import org.springframework.stereotype.Component;

/** Loads snippets after checking the current user may act on them. */
@Component
public class SnippetAccess {

    private final SnippetRepository repository;
    private final PermissionService permissions;

    public SnippetAccess(SnippetRepository repository, PermissionService permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    /**
     * Role is checked before existence so that users cannot probe which ids exist.
     *
     * @throws ForbiddenException if the current user's role is below {@code required}
     * @throws NotFoundException if the snippet does not exist
     */
    public Snippet require(UUID snippetId, Role required) {
        if (!permissions.roleOf(snippetId).atLeast(required)) {
            throw new ForbiddenException(
                    "%s role required over snippet %s".formatted(required, snippetId));
        }
        return find(snippetId);
    }

    /** Loads a snippet without checking permissions; for internal processes only. */
    public Snippet find(UUID snippetId) {
        return repository
                .findById(snippetId)
                .orElseThrow(
                        () -> new NotFoundException("Snippet %s not found".formatted(snippetId)));
    }
}
