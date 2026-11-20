package keystrokesmod.script;

import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import java.lang.reflect.InvocationTargetException;

public final class ContainerCompilation {
    private ContainerCompilation() {
    }

    public static JavaFileManager fileManager(StandardJavaFileManager standard, ClassLoader loader) {
        try {
            return (JavaFileManager) loader.getClass()
                    .getMethod("compilerFileManager", StandardJavaFileManager.class)
                    .invoke(loader, standard);
        } catch (NoSuchMethodException ignored) {
            return standard;
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Unable to access script classpath", failure);
        }
    }
}
