package uk.betacraft.legacyfix.jvm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import sun.misc.Unsafe;

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
        Class<?> type = field.getType();
        Object converted = type.isPrimitive() ? widen(value, type, field) : value;
        if (!type.isPrimitive() && value != null && !type.isInstance(value)) {
            throw new IllegalArgumentException("Can't set " + type.getName() + " field " + field + " to " + value.getClass().getName());
        }

        Class<?> owner = field.getDeclaringClass();
        try {
            Class.forName(owner.getName(), true, owner.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }

        Unsafe unsafe = UnsafeReflection.getUnsafe();
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);

        if (type == int.class) {
            unsafe.putIntVolatile(base, offset, (Integer) converted);
        } else if (type == long.class) {
            unsafe.putLongVolatile(base, offset, (Long) converted);
        } else if (type == float.class) {
            unsafe.putFloatVolatile(base, offset, (Float) converted);
        } else if (type == double.class) {
            unsafe.putDoubleVolatile(base, offset, (Double) converted);
        } else if (type == boolean.class) {
            unsafe.putBooleanVolatile(base, offset, (Boolean) converted);
        } else if (type == byte.class) {
            unsafe.putByteVolatile(base, offset, (Byte) converted);
        } else if (type == short.class) {
            unsafe.putShortVolatile(base, offset, (Short) converted);
        } else if (type == char.class) {
            unsafe.putCharVolatile(base, offset, (Character) converted);
        } else {
            unsafe.putObjectVolatile(base, offset, converted);
        }
    }

    private static Object widen(Object value, Class<?> type, Field field) {
        if (value instanceof Boolean) {
            if (type == boolean.class) {
                return value;
            }
        } else if (value instanceof Character) {
            char c = (Character) value;
            if (type == char.class) return value;
            if (type == int.class) return (int) c;
            if (type == long.class) return (long) c;
            if (type == float.class) return (float) c;
            if (type == double.class) return (double) c;
        } else if (value instanceof Number) {
            Number n = (Number) value;
            int rank = rank(value.getClass());
            int targetRank = rank(type);
            if (rank >= 0 && targetRank >= rank) {
                if (type == byte.class) return n.byteValue();
                if (type == short.class) return n.shortValue();
                if (type == int.class) return n.intValue();
                if (type == long.class) return n.longValue();
                if (type == float.class) return n.floatValue();
                if (type == double.class) return n.doubleValue();
            }
        }

        throw new IllegalArgumentException("Can't set " + type.getName() + " field " + field + " to " + (value == null ? "null" : value.getClass().getName()));
    }

    private static int rank(Class<?> type) {
        if (type == Byte.class || type == byte.class) return 0;
        if (type == Short.class || type == short.class) return 1;
        if (type == Integer.class || type == int.class) return 2;
        if (type == Long.class || type == long.class) return 3;
        if (type == Float.class || type == float.class) return 4;
        if (type == Double.class || type == double.class) return 5;
        return -1;
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
