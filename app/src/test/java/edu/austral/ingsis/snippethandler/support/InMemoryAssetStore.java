package edu.austral.ingsis.snippethandler.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import edu.austral.ingsis.snippethandler.asset.AssetStore;

/** Stand-in for the asset service; the HTTP client is covered in the asset-client module. */
public class InMemoryAssetStore implements AssetStore {

    private final Map<String, String> assets = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String content) {
        assets.put(key, content);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(assets.get(key));
    }

    @Override
    public void delete(String key) {
        assets.remove(key);
    }

    public boolean contains(String key) {
        return assets.containsKey(key);
    }

    public int size() {
        return assets.size();
    }

    public void clear() {
        assets.clear();
    }
}
