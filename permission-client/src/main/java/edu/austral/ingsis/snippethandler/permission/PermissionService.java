package edu.austral.ingsis.snippethandler.permission;

import java.util.List;
import java.util.UUID;

/**
 * Permissions service: knows who owns each snippet and with whom it is shared. Every call acts on
 * behalf of the authenticated user whose token is forwarded with the request.
 */
public interface PermissionService {

    /** Registers the current user as the owner of a new snippet. */
    void registerOwner(UUID snippetId);

    /** Role of the current user over the snippet; {@link Role#NONE} if they have no access. */
    Role roleOf(UUID snippetId);

    /**
     * Gives another user read access over the snippet and its tests. Sharing again with the same
     * user is not an error.
     */
    void share(UUID snippetId, String userId);

    /** Ids of the snippets the current user can access through the given relation. */
    List<UUID> accessibleSnippets(Relation relation);

    /** Forgets every permission over a deleted snippet. */
    void removeSnippet(UUID snippetId);
}
