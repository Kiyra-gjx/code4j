package code4j.permissions.store;

import code4j.permissions.model.PermissionResource;

import java.util.List;
import java.util.Optional;

public interface PermissionStore {
    Optional<PermissionStoreEntry> find(PermissionResource resource);
    void save(PermissionStoreEntry entry);
    List<PermissionStoreEntry> entries();

    static PermissionStore none() {
        return new PermissionStore() {
            @Override public Optional<PermissionStoreEntry> find(PermissionResource r) { return Optional.empty(); }
            @Override public void save(PermissionStoreEntry e) {}
            @Override public List<PermissionStoreEntry> entries() { return List.of(); }
        };
    }
}
