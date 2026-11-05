package uk.betacraft.legacyfix.jvm.sunreflect;

import uk.betacraft.legacyfix.jvm.TrustedLookup;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

class LookupConstructorAccessor implements ConstructorAccessor {
    private final Constructor<?> constructor;
    private Object handle;

    LookupConstructorAccessor(Constructor<?> constructor) {
        this.constructor = constructor;
    }

    public Object newInstance(Object[] args) throws InvocationTargetException {
        java.lang.invoke.MethodHandle handle;
        try {
            if (this.handle == null) {
                this.handle = ((java.lang.invoke.MethodHandles.Lookup) TrustedLookup.get()).unreflectConstructor(constructor);
            }
            handle = (java.lang.invoke.MethodHandle) this.handle;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Couldn't access " + constructor, e);
        }

        try {
            return handle.invokeWithArguments(args == null ? new Object[0] : args);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Wrong arguments for " + constructor, e);
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }
}
