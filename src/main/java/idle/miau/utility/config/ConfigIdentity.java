package idle.miau.utility.config;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class ConfigIdentity {
    private static final Map<String, String> classes = new HashMap<>();
    private static final Map<String, String> fields = new HashMap<>();

    private ConfigIdentity() {
    }

    public static String className(Class<?> type) {
        String name = type.getName();
        return classes.getOrDefault(name, name);
    }

    public static String simpleName(Class<?> type) {
        if (!classes.containsKey(type.getName())) return type.getSimpleName();
        String name = className(type);
        return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
    }

    public static String fieldName(Field field) {
        return fields.getOrDefault(field.getDeclaringClass().getName() + '#' + field.getName(), field.getName());
    }
}
