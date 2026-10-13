package edu.austral.ingsis.snippethandler.snippet;

import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Persists a snippet as a unit: its row in the database, its source in the bucket and whatever the
 * caller needs to happen along with them. If any step fails the row is rolled back and the bucket
 * is restored. The transaction only lives for these writes; anything slow (validating, linting,
 * formatting) is expected to be done before calling.
 */
@Component
public class SnippetStore {

    private final SnippetRepository repository;
    private final SnippetContents contents;
    private final TransactionOperations transaction;

    public SnippetStore(
            SnippetRepository repository,
            SnippetContents contents,
            TransactionOperations transaction) {
        this.repository = repository;
        this.contents = contents;
        this.transaction = transaction;
    }

    /**
     * @param content source of the snippet after this save
     * @param previousContent source currently in the bucket, or null if the snippet is new; the
     *     bucket is only written when both differ
     * @param alongside runs inside the transaction with the saved snippet; if it throws, everything
     *     is undone
     * @return the saved snippet
     */
    public Snippet save(
            Snippet snippet, String content, String previousContent, Consumer<Snippet> alongside) {
        return transaction.execute(
                status -> {
                    Snippet saved = repository.saveAndFlush(snippet);
                    if (!content.equals(previousContent)) {
                        contents.write(saved.getId(), content, previousContent);
                    }
                    alongside.accept(saved);
                    return saved;
                });
    }

    /**
     * @param alongside runs inside the transaction; if it throws, nothing is deleted
     */
    public void delete(UUID snippetId, Runnable alongside) {
        transaction.executeWithoutResult(
                status -> {
                    repository.deleteById(snippetId);
                    alongside.run();
                    contents.deleteAfterCommit(snippetId);
                });
    }
}
