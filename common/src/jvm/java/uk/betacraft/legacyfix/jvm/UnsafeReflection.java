package uk.betacraft.legacyfix.jvm;

import sun.misc.Unsafe;
import uk.betacraft.legacyfix.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@SuppressWarnings("unused")
public class UnsafeReflection {
    private UnsafeReflection() {
    }

    public static Unsafe getUnsafe() {
        return UnsafeHolder.UNSAFE;
    }

    public static void setFieldValue(Field field, Object target, Object value) {
        Class<?> type = field.getType();
        Object converted;
        if (type.isPrimitive()) {
            converted = widen(value, type, field);
        } else {
            if (value != null && !type.isInstance(value)) {
                throw new IllegalArgumentException("Can't set " + type.getName() + " field " + field + " to " + value.getClass().getName());
            }
            converted = value;
        }

        Unsafe unsafe = getUnsafe();
        Object base;
        long offset;
        if (Modifier.isStatic(field.getModifiers())) {
            Class<?> owner = field.getDeclaringClass();
            try {
                Class.forName(owner.getName(), true, owner.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }

            base = unsafe.staticFieldBase(field);
            offset = unsafe.staticFieldOffset(field);
        } else {
            if (target == null) {
                throw new NullPointerException("Can't set instance field " + field + " on null");
            }
            if (!field.getDeclaringClass().isInstance(target)) {
                throw new IllegalArgumentException("Can't set " + field + " on " + target.getClass().getName());
            }

            base = target;
            offset = unsafe.objectFieldOffset(field);
        }

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

    private static class UnsafeHolder {
        static final Unsafe UNSAFE = readUnsafe();

        private static Unsafe readUnsafe() {
            try {
                Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
                unsafe.setAccessible(true);
                return (Unsafe) unsafe.get(null);
            } catch (Exception e) {
                Logger.error("UnsafeReflection#getUnsafe", e);
                throw new RuntimeException("Failed to get unsafe!", e);
            }
        }
    }
}
