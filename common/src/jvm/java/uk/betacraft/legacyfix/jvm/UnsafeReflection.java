package uk.betacraft.legacyfix.jvm;

import sun.misc.Unsafe;
import uk.betacraft.legacyfix.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class UnsafeReflection {
    private UnsafeReflection() {
    }

    public static Unsafe getUnsafe() {
        return UnsafeHolder.UNSAFE;
    }

    public static <T extends Enum<T>> T addEnum(Class<T> enumClass, String name, Class<?>[] paramTypes, Object... args) {
        if (enumClass == null) {
            throw new IllegalArgumentException("enumClass can't be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name can't be null");
        }
        if (!enumClass.isEnum()) {
            throw new IllegalArgumentException(enumClass.getName() + " is not an enum");
        }

        T[] constants = enumClass.getEnumConstants();
        for (T constant : constants) {
            if (constant.name().equals(name)) {
                throw new IllegalArgumentException(enumClass.getName() + " already has a constant named " + name);
            }
        }

        Class<?>[] parameterTypes = paramTypes == null ? new Class<?>[0] : paramTypes;
        Object[] arguments = args == null ? new Object[0] : args;
        if (parameterTypes.length != arguments.length) {
            throw new IllegalArgumentException("Expected " + parameterTypes.length + " arguments, got " + arguments.length);
        }

        T constant = createConstant(enumClass, name, constants.length, parameterTypes, arguments);
        register(enumClass, constants, constant);
        return constant;
    }

    public static Object newInstance(Constructor<?> constructor, Object[] args) {
        try {
            return ((java.lang.invoke.MethodHandles.Lookup) TrustedLookup.get())
                .unreflectConstructor(constructor)
                .invokeWithArguments(args);
        } catch (Throwable t) {
            throw new IllegalStateException("Could not construct " + constructor.getDeclaringClass().getName(), t);
        }
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

    private static <T extends Enum<T>> T createConstant(Class<T> enumClass, String name, int ordinal,
        Class<?>[] paramTypes, Object[] args) {
        Class<?>[] constructorParams = new Class<?>[paramTypes.length + 2];
        constructorParams[0] = String.class;
        constructorParams[1] = int.class;
        System.arraycopy(paramTypes, 0, constructorParams, 2, paramTypes.length);

        Object[] constructorArgs = new Object[args.length + 2];
        constructorArgs[0] = name;
        constructorArgs[1] = ordinal;
        System.arraycopy(args, 0, constructorArgs, 2, args.length);

        try {
            java.lang.invoke.MethodHandle constructor = ((java.lang.invoke.MethodHandles.Lookup) TrustedLookup.get())
                .findConstructor(enumClass, java.lang.invoke.MethodType.methodType(void.class, constructorParams));
            return enumClass.cast(constructor.invokeWithArguments(constructorArgs));
        } catch (Throwable t) {
            throw new IllegalStateException("Could not construct " + enumClass.getName() + "." + name, t);
        }
    }

    private static <T extends Enum<T>> void register(Class<T> enumClass, T[] constants, T constant) {
        T[] newConstants = Arrays.copyOf(constants, constants.length + 1);
        newConstants[constants.length] = constant;

        Map<String, T> directory = new HashMap<String, T>(newConstants.length * 2);
        for (T value : newConstants) {
            directory.put(value.name(), value);
        }

        try {
            Unsafe unsafe = getUnsafe();

            Field values = enumClass.getDeclaredField("$VALUES");
            unsafe.putObject(null, unsafe.staticFieldOffset(values), newConstants);

            Field enumConstants = Class.class.getDeclaredField("enumConstants");
            unsafe.putObject(enumClass, unsafe.objectFieldOffset(enumConstants), newConstants);

            Field enumConstantDirectory = Class.class.getDeclaredField("enumConstantDirectory");
            unsafe.putObject(enumClass, unsafe.objectFieldOffset(enumConstantDirectory), directory);
        } catch (Exception e) {
            throw new IllegalStateException("Could not register " + enumClass.getName() + "." + constant.name(), e);
        }
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
