package uk.betacraft.legacyfix.jvm;

import uk.betacraft.legacyfix.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;

public class AppletPolyfill {
    private static final String[] CLASSES = {
        "java/applet/AppletContext",
        "java/applet/AppletStub",
        "java/applet/Applet"
    };

    public static void install() throws Throwable {
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) TrustedLookup.get();
        Class<?> unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
        Object unsafe = lookup.findStatic(unsafeClass, "getUnsafe", MethodType.methodType(unsafeClass)).invokeWithArguments();
        MethodHandle defineClass = lookup.findVirtual(
            unsafeClass, "defineClass",
            MethodType.methodType(
                Class.class, String.class, byte[].class, int.class, int.class,
                ClassLoader.class, ProtectionDomain.class
            )
        );

        for (String name : CLASSES) {
            InputStream in = AppletPolyfill.class.getResourceAsStream("/" + name + ".class");
            if (in == null) {
                throw new IOException("Missing polyfill class " + name);
            }

            byte[] bytes = StreamUtils.readInputStream(in);
            in.close();

            defineClass.invokeWithArguments(unsafe, name.replace('/', '.'), bytes, 0, bytes.length, null, null);
        }
    }
}
