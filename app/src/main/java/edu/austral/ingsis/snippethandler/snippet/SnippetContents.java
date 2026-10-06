package edu.austral.ingsis.snippethandler.snippet;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.asset.AssetStore;
import edu.austral.ingsis.snippethandler.asset.AssetStoreException;
import edu.austral.ingsis.snippethandler.error.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Snippet sources in the asset store, kept consistent with the database transaction: writes are
 * undone if the transaction rolls back and deletes only happen once it commits.
 */
@Component
public class SnippetContents {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetContents.class);

    private final AssetStore assets;

    public SnippetContents(AssetStore assets) {
        this.assets = assets;
    }

    public String read(UUID snippetId) {
        return assets.get(key(snippetId))
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Content of snippet %s not found".formatted(snippetId)));
    }

    /**
     * @param previous content to restore on rollback, or null if the snippet is new
     */
    public void write(UUID snippetId, String content, String previous) {
        assets.put(key(snippetId), content);
        afterCompletion(
                status -> {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        restore(snippetId, previous);
                    }
                });
    }

    public void deleteAfterCommit(UUID snippetId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            assets.delete(key(snippetId));
            return;
        }
        afterCompletion(
                status -> {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        quietly(() -> assets.delete(key(snippetId)), snippetId);
                    }
                });
    }

    private void restore(UUID snippetId, String previous) {
        quietly(
                () -> {
                    if (previous == null) {
                        assets.delete(key(snippetId));
                    } else {
                        assets.put(key(snippetId), previous);
                    }
                },
                snippetId);
    }

    private static void quietly(Runnable action, UUID snippetId) {
        try {
            action.run();
        } catch (AssetStoreException e) {
            LOG.error("Asset of snippet {} left inconsistent", snippetId, e);
        }
    }

    private static void afterCompletion(StatusCallback callback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            callback.on(status);
                        }
                    });
        }
    }

    private static String key(UUID snippetId) {
        return snippetId.toString();
    }

    @FunctionalInterface
    private interface StatusCallback {
        void on(int status);
    }
}
