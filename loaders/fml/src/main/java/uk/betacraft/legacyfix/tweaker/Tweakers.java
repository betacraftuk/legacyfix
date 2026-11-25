package uk.betacraft.legacyfix.tweaker;

import javassist.ClassPool;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Set;

public class Tweakers {
    @SuppressWarnings("unchecked")
    public static void removeLwjglException(Object classLoader) {
        try {
            Field exceptionsField = classLoader.getClass().getDeclaredField("classLoaderExceptions");
            exceptionsField.setAccessible(true);

            Set<String> exceptions = (Set<String>) exceptionsField.get(classLoader);
            exceptions.remove("org.lwjgl.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassPool createClassPool(URL[] urls) {
        ClassPool pool = new ClassPool(true);
        try {
            for (URL url : urls) {
                pool.appendClassPath(url.toURI().getPath());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return pool;
    }
}
