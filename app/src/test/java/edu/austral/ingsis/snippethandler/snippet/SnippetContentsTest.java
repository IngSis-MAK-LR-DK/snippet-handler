package edu.austral.ingsis.snippethandler.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.asset.AssetStoreException;
import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.support.InMemoryAssetStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SnippetContentsTest {

    private static final UUID ID = UUID.randomUUID();

    private final InMemoryAssetStore assets = new InMemoryAssetStore();
    private final SnippetContents contents = new SnippetContents(assets);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void readsStoredContent() {
        contents.write(ID, "x;", null);

        assertThat(contents.read(ID)).isEqualTo("x;");
        assertThatThrownBy(() -> contents.read(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rollbackRemovesNewContent() {
        TransactionSynchronizationManager.initSynchronization();
        contents.write(ID, "x;", null);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(assets.contains(ID.toString())).isFalse();
    }

    @Test
    void rollbackRestoresPreviousContent() {
        assets.put(ID.toString(), "old");
        TransactionSynchronizationManager.initSynchronization();
        contents.write(ID, "new", "old");

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(assets.get(ID.toString())).contains("old");
    }

    @Test
    void commitKeepsNewContent() {
        TransactionSynchronizationManager.initSynchronization();
        contents.write(ID, "new", "old");

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(assets.get(ID.toString())).contains("new");
    }

    @Test
    void deleteWaitsForCommit() {
        assets.put(ID.toString(), "x;");
        TransactionSynchronizationManager.initSynchronization();
        contents.deleteAfterCommit(ID);
        assertThat(assets.contains(ID.toString())).isTrue();

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(assets.contains(ID.toString())).isFalse();
    }

    @Test
    void deleteWithoutTransactionIsImmediate() {
        assets.put(ID.toString(), "x;");

        contents.deleteAfterCommit(ID);

        assertThat(assets.contains(ID.toString())).isFalse();
    }

    @Test
    void compensationFailuresAreSwallowed() {
        SnippetContents failing =
                new SnippetContents(
                        new InMemoryAssetStore() {
                            @Override
                            public void delete(String key) {
                                throw new AssetStoreException("down", null);
                            }
                        });
        TransactionSynchronizationManager.initSynchronization();
        failing.write(ID, "x;", null);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private static void complete(int status) {
        for (TransactionSynchronization sync :
                TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(status);
        }
    }
}
