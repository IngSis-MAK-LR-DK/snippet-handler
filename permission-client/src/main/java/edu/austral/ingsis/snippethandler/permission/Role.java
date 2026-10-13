package edu.austral.ingsis.snippethandler.permission;

/** Role of a user over a snippet. Declared from least to most privileged. */
public enum Role {
    /** No access at all. */
    NONE,
    /** Can read the snippet and its tests, and run them. */
    READER,
    /** Created the snippet: can edit, share and delete it. */
    OWNER;

    public boolean atLeast(Role required) {
        return compareTo(required) >= 0;
    }
}
