package idle.miau.patcher.dedup;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeduplicationCache<T> {
    private final Map<T, T> pool = new ConcurrentHashMap<>();

    public T deduplicate(T item) {
        if (item == null) {
            return null;
        }
        T existing = pool.putIfAbsent(item, item);
        return existing != null ? existing : item;
    }

    public void clear() {
        pool.clear();
    }

    public int size() {
        return pool.size();
    }
}
