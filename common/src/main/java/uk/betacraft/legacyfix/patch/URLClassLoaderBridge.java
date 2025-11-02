package uk.betacraft.legacyfix.patch;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/**
 * Mimics Java 8's system classloader on Java 9+ <br>
 * (for ModLoader and other, heavy mods) <br>
 * <br>
 * Usage: -Djava.system.class.loader=uk.betacraft.legacyfix.patch.URLClassLoaderBridge
 */
@SuppressWarnings("unused")
public class URLClassLoaderBridge extends URLClassLoader {
    private static final ArrayList<URL> urls = new ArrayList<URL>();

    static {
        String classpath = System.getProperty("java.class.path");
        String[] classpathSplit = classpath.split(File.pathSeparator);

        for (String singlePath : classpathSplit) {
            try {
                urls.add(new File(singlePath).toURI().toURL());
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public URLClassLoaderBridge(ClassLoader loader) {
        super(urls.toArray(new URL[0]), loader);
    }

    void appendToClassPathForInstrumentation(String path) throws MalformedURLException {
        super.addURL(new File(path).toURI().toURL());
    }
}