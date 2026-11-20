package uk.betacraft.legacyfix.jvm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


@SuppressWarnings("unused")
public class FinalFieldRemover {
    private static final VH MODIFIERS = handle("modifiers", int.class);
    private static final VH FIELD_ACCESSOR = handle("fieldAccessor", accessorType());
    private static final VH OVERRIDE_FIELD_ACCESSOR = handle("overrideFieldAccessor", accessorType());
    private static final VH ROOT = handle("root", Field.class);

    private FinalFieldRemover() {
    }

    private static final Set<Field> UNFINAL_STATICS = Collections.synchronizedSet(new HashSet<Field>());

    public static void removeFinal(Field field) {
        int modifiers = (Integer) MODIFIERS.get(field);
        if ((modifiers & Modifier.FINAL) == 0) {
            return;
        }

        if ((modifiers & Modifier.STATIC) != 0) {
            UNFINAL_STATICS.add(field);
            return;
        }

        MODIFIERS.set(field, modifiers & ~Modifier.FINAL);
        invalidateAccessors(field);

        Field root = (Field) ROOT.get(field);
        if (root != null) {
            invalidateAccessors(root);
        }
    }

    public static boolean isUnfinalStatic(Field field) {
        return UNFINAL_STATICS.contains(field);
    }

    public static int modifiers(Field field) {
        int modifiers = field.getModifiers();
        return isUnfinalStatic(field) ? modifiers & ~Modifier.FINAL : modifiers;
    }

    public static void setStatic(Field field, Object value) {
        UnsafeReflection.setFieldValue(field, null, value);
    }

    private static void invalidateAccessors(Field field) {
        FIELD_ACCESSOR.set(field, null);
        OVERRIDE_FIELD_ACCESSOR.set(field, null);
    }

    private static VH handle(String name, Class<?> type) {
        try {
            java.lang.invoke.MethodHandles.Lookup lookup = (java.lang.invoke.MethodHandles.Lookup) TrustedLookup.get();
            return new VH(
                lookup.findGetter(Field.class, name, type),
                lookup.findSetter(Field.class, name, type)
            );
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Class<?> accessorType() {
        try {
            return Class.forName("jdk.internal.reflect.FieldAccessor");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static class VH {
        private final Object getter;
        private final Object setter;

        VH(Object getter, Object setter) {
            this.getter = getter;
            this.setter = setter;
        }

        Object get(Object target) {
            try {
                return ((java.lang.invoke.MethodHandle) getter).invokeWithArguments(target);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

        void set(Object target, Object value) {
            try {
                ((java.lang.invoke.MethodHandle) setter).invokeWithArguments(target, value);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
    }
}
