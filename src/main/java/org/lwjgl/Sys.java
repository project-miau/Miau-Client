
package org.lwjgl;


import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

@SuppressWarnings("UnusedReturnValue")
public class Sys {

    public static long getTime() {
        return System.nanoTime();
    }

    public static long getTimerResolution() {
        return 1_000_000_000L;
    }

    public static boolean openURL(String url) {
        
        try {
            
            final Class<?> serviceManagerClass = Class.forName("javax.jnlp.ServiceManager");
            Method lookupMethod = AccessController.doPrivileged((PrivilegedExceptionAction<Method>) () -> serviceManagerClass.getMethod("lookup", String.class));
            Object basicService = lookupMethod.invoke(serviceManagerClass, "javax.jnlp.BasicService");
            final Class<?> basicServiceClass = Class.forName("javax.jnlp.BasicService");
            Method showDocumentMethod = AccessController.doPrivileged((PrivilegedExceptionAction<Method>) () -> basicServiceClass.getMethod("showDocument", URL.class));
            try {
                return (Boolean) showDocumentMethod.invoke(basicService, new URL(url));
            } catch (MalformedURLException e) {
                e.printStackTrace(System.err);
                return false;
            }
        } catch (Exception ue) {
            ue.printStackTrace();
            return false;
        }
    }

    public static String getVersion() {
        return Version.getVersion();
    }
}
