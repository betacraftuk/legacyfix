package uk.betacraft.legacyfix.jvm.sunreflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.PrivilegedAction;

@SuppressWarnings("unused")
public class ReflectionFactory {
    private static final ReflectionFactory INSTANCE = new ReflectionFactory();

    protected ReflectionFactory() {
    }

    public static ReflectionFactory getReflectionFactory() {
        return INSTANCE;
    }

    public FieldAccessor newFieldAccessor(Field field, boolean override) {
        return new UnsafeFieldAccessor(field);
    }

    public ConstructorAccessor newConstructorAccessor(Constructor<?> constructor) {
        return new LookupConstructorAccessor(constructor);
    }

    public static final class GetReflectionFactoryAction implements PrivilegedAction<ReflectionFactory> {
        public ReflectionFactory run() {
            return getReflectionFactory();
        }
    }
}
