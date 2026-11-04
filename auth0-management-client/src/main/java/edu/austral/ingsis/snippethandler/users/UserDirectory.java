package edu.austral.ingsis.snippethandler.users;

import java.util.List;

/** Searches the users of the identity provider, so snippets can be shared with them. */
public interface UserDirectory {

    /** Users whose name or email starts with {@code query}. A blank query finds nobody. */
    List<UserView> search(String query, int page, int size);
}
