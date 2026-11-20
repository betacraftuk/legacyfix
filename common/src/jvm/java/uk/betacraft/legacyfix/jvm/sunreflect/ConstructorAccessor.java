package uk.betacraft.legacyfix.jvm.sunreflect;

import java.lang.reflect.InvocationTargetException;

@SuppressWarnings("unused")
public interface ConstructorAccessor {
    Object newInstance(Object[] args) throws InstantiationException, IllegalArgumentException, InvocationTargetException;
}
