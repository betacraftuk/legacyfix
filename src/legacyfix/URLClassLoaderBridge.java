package legacyfix;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/**
 * To mimic Java 8's system classloader on Java 9+
 * (for ModLoader and other, heavy mods)
 * 
 * Usage: -Djava.system.class.loader=legacyfix.URLClassLoaderBridge
 * Make sure to include LegacyFix (or that single class) in your path!!!
 *
 */
public class URLClassLoaderBridge extends URLClassLoader {
	private static ArrayList<URL> urls = new ArrayList<URL>();

	static {
		String cp = System.getProperty("java.class.path");
		String[] cpSplit = cp.split(File.pathSeparator);

		for (String singlePath : cpSplit) {
			try {
				urls.add(new File(singlePath).toURI().toURL());
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	public URLClassLoaderBridge(ClassLoader cloader) {
		// parent = null
		super(urls.toArray(new URL[urls.size()]), null);
	}

	// Allows for javaagent to be hooked
	void appendToClassPathForInstrumentation(String path) throws MalformedURLException {
		super.addURL(new File(path).toURI().toURL());
	}
}
