package idle.miau.helper;

import java.lang.reflect.Field;

public final class ReflectionHelper {
    private ReflectionHelper() {
    }

    public static Field findField(Class<?> type, String... names) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        throw new IllegalStateException("Missing field in " + type.getName());
    }
}
