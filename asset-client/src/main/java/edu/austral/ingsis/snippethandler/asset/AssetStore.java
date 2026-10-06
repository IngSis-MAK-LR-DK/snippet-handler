package edu.austral.ingsis.snippethandler.asset;

import java.util.Optional;

/** Bucket where the content of each snippet is stored, addressed by key. */
public interface AssetStore {

    void put(String key, String content);

    Optional<String> get(String key);

    void delete(String key);
}
