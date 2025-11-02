package net.minecraft.launchwrapper;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

public class LaunchClassLoader extends URLClassLoader {
    private Set<String> classLoaderExceptions = new HashSet<String>();

    public LaunchClassLoader(URL[] urls) {
        super(urls, null);
    }

    public void registerTransformer(String transformerClassName) {
    }
}
