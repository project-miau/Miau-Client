package idle.miau.patcher.dedup;

public class IdentifierCaches {
    public static final DeduplicationCache<String> NAMESPACES = new DeduplicationCache<>();
    public static final DeduplicationCache<String> PATH = new DeduplicationCache<>();

    public static String deduplicateNamespace(String namespace) {
        return namespace == null ? null : NAMESPACES.deduplicate(namespace);
    }

    public static String deduplicatePath(String path) {
        return path == null ? null : PATH.deduplicate(path);
    }
}
