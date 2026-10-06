package edu.austral.ingsis.snippethandler.snippet;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.ForbiddenException;
import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.permission.Permission;
import edu.austral.ingsis.snippethandler.permission.PermissionService;

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
     * Permission is checked before existence so that users cannot probe which ids exist.
     *
     * @throws ForbiddenException if the current user lacks {@code permission}
     * @throws NotFoundException if the snippet does not exist
     */
    public Snippet require(UUID snippetId, Permission permission) {
        if (!permissions.hasPermission(snippetId, permission)) {
            throw new ForbiddenException(
                    "Missing %s permission over snippet %s".formatted(permission, snippetId));
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
