package uk.betacraft.legacyfix.jvm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

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
            MethodHandle addUrl = lookup().findVirtual(urlClassPathClass(), "addURL", MethodType.methodType(void.class, URL.class));
            addUrl.invokeWithArguments(systemClassPath(), url);
        } catch (Throwable t) {
            throw new IllegalStateException("Could not add " + url + " to the class path", t);
        }
    }

    public static URL[] getClassPath() {
        try {
            MethodHandle getUrls = lookup().findVirtual(urlClassPathClass(), "getURLs", MethodType.methodType(URL[].class));
            return (URL[]) getUrls.invokeWithArguments(systemClassPath());
        } catch (Throwable t) {
            throw new IllegalStateException("Could not read the class path", t);
        }
    }

    private static Object systemClassPath() throws Throwable {
        Class<?> builtinLoader = Class.forName("jdk.internal.loader.BuiltinClassLoader");
        return lookup()
            .findGetter(builtinLoader, "ucp", urlClassPathClass())
            .invokeWithArguments(ClassLoader.getSystemClassLoader());
    }

    private static Class<?> urlClassPathClass() throws ClassNotFoundException {
        return Class.forName("jdk.internal.loader.URLClassPath");
    }

    private static MethodHandles.Lookup lookup() {
        return (MethodHandles.Lookup) TrustedLookup.get();
    }

    private static class SystemLoaderProxy extends URLClassLoader {
        SystemLoaderProxy() {
            super(new URL[0], ClassLoader.getSystemClassLoader());
        }

        @Override
        public URL[] getURLs() {
            return getClassPath();
        }

        @Override
        protected void addURL(URL url) {
            appendToClassPath(url);
        }
    }
}
