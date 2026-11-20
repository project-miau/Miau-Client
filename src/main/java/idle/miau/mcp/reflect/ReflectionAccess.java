package idle.miau.mcp.reflect;

import java.lang.reflect.Field;

public final class ReflectionAccess {
    private ReflectionAccess() {
    }

    public static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        throw new IllegalArgumentException("Unable to find field on " + owner.getName());
    }

    @SuppressWarnings("unchecked")
    public static <T, E> T getPrivateValue(Class<? super E> owner, E instance, String... names) {
        try {
            return (T) findField(owner, names).get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T, E> void setPrivateValue(Class<? super E> owner, E instance, T value, String... names) {
        try {
            findField(owner, names).set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
