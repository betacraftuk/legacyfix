package uk.betacraft.legacyfix.jvm;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ClassLoaderHelper {
    private static URLClassLoader proxy;

    private ClassLoaderHelper() {
    }

    public static boolean isSystemLoader(Object loader) {
        return loader != null && loader == ClassLoader.getSystemClassLoader() && !(loader instanceof URLClassLoader);
    }

    public static synchronized URLClassLoader asURLClassLoader(Object loader) {
        if (isSystemLoader(loader)) {
            if (proxy == null) {
                proxy = new SystemLoaderProxy();
            }
            return proxy;
        }

        return (URLClassLoader) loader;
    }

    public static void appendToClassPath(URL url) {
        try {
            MethodHandles.Lookup lookup = (MethodHandles.Lookup) TrustedLookup.get();
            Class<?> builtinLoader = Class.forName("jdk.internal.loader.BuiltinClassLoader");
            Class<?> urlClassPath = Class.forName("jdk.internal.loader.URLClassPath");

            Object ucp = lookup
                .findGetter(builtinLoader, "ucp", urlClassPath)
                .invokeWithArguments(ClassLoader.getSystemClassLoader());

            MethodHandle addUrl = lookup.findVirtual(urlClassPath, "addURL", MethodType.methodType(void.class, URL.class));
            addUrl.invokeWithArguments(ucp, url);
        } catch (Throwable t) {
            throw new IllegalStateException("Could not add " + url + " to the class path", t);
        }
    }

    private static List<URL> getClassPath() {
        List<URL> urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.length() > 0) {
                try {
                    urls.add(new File(entry).toURI().toURL());
                } catch (Exception ignored) {
                }
            }
        }
        return urls;
    }

    private static class SystemLoaderProxy extends URLClassLoader {
        private final List<URL> urls = getClassPath();

        SystemLoaderProxy() {
            super(new URL[0], ClassLoader.getSystemClassLoader());
        }

        @Override
        public synchronized URL[] getURLs() {
            return urls.toArray(new URL[0]);
        }

        @Override
        protected synchronized void addURL(URL url) {
            appendToClassPath(url);
            urls.add(url);
        }
    }
}
