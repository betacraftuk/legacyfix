package uk.betacraft.legacyfix.jvm;

import java.lang.reflect.Field;

@SuppressWarnings("unused")
public class ThreadTargetStub {
    private Runnable target;

    private static Object holderGetter;
    private static Object taskGetter;

    private ThreadTargetStub() {
    }

    public static Field field() {
        try {
            return ThreadTargetStub.class.getDeclaredField("target");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isTargetField(Field field) {
        return field.getDeclaringClass() == ThreadTargetStub.class;
    }

    public static Object read(Object thread) {
        if (!(thread instanceof Thread)) {
            throw new IllegalArgumentException("Can't get " + Runnable.class.getName() + " field java.lang.Thread.target on " +
                (thread == null ? "null" : thread.getClass().getName()));
        }

        try {
            if (holderGetter == null) {
                java.lang.invoke.MethodHandles.Lookup lookup = (java.lang.invoke.MethodHandles.Lookup) TrustedLookup.get();
                Class<?> holderClass = Class.forName("java.lang.Thread$FieldHolder");
                taskGetter = lookup.findGetter(holderClass, "task", Runnable.class);
                holderGetter = lookup.findGetter(Thread.class, "holder", holderClass);
            }

            Object holder = ((java.lang.invoke.MethodHandle) holderGetter).invokeWithArguments(thread);
            return ((java.lang.invoke.MethodHandle) taskGetter).invokeWithArguments(holder);
        } catch (Throwable t) {
            throw new IllegalStateException("Couldn't read the target of " + thread, t);
        }
    }
}
