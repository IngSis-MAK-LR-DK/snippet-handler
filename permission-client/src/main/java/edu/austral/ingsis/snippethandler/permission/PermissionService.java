package edu.austral.ingsis.snippethandler.permission;

import java.util.List;
import java.util.UUID;

/**
 * Permissions service: knows who owns each snippet and with whom it is shared. Every call acts on
 * behalf of the authenticated user whose token is forwarded with the request, except {@link
 * #registerOwner}.
 */
public interface PermissionService {

    /**
     * Registers {@code ownerId} as the owner of a new snippet. It is the handler who vouches for
     * who the owner is, so this call is made with the handler's own service token and not with the
     * caller's: the permissions service only accepts it from a trusted service, otherwise any user
     * could claim a snippet that has no owner yet.
     */
    void registerOwner(UUID snippetId, String ownerId);

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
