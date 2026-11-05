package uk.betacraft.legacyfix.jvm;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class TrustedLookup {
    private static Object implLookup = null;

    private TrustedLookup() {
    }

    public static Object get() {
        if (implLookup == null) {
            implLookup = readImplLookup();
        }
        return implLookup;
    }

    private static Object readImplLookup() {
        try {
            java.lang.invoke.MethodHandles.lookup();
            Unsafe unsafe = UnsafeReflection.getUnsafe();

            Field implLookup = java.lang.invoke.MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object base = unsafe.staticFieldBase(implLookup);
            return unsafe.getObject(base, unsafe.staticFieldOffset(implLookup));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
