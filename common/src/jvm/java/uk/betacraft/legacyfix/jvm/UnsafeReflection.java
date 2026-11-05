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
        if (field.getType().isPrimitive()) {
            throw new IllegalArgumentException(field + " is not a reference field");
        }

        Unsafe unsafe = getUnsafe();
        if (Modifier.isStatic(field.getModifiers())) {
            unsafe.putObjectVolatile(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
        } else {
            unsafe.putObjectVolatile(target, unsafe.objectFieldOffset(field), value);
        }
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
