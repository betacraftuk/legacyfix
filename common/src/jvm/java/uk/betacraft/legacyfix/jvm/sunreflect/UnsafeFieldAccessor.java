package uk.betacraft.legacyfix.jvm.sunreflect;

import uk.betacraft.legacyfix.jvm.UnsafeReflection;

import java.lang.reflect.Field;

class UnsafeFieldAccessor implements FieldAccessor {
    private final Field field;

    UnsafeFieldAccessor(Field field) {
        this.field = field;
        try {
            field.setAccessible(true);
        } catch (RuntimeException ignored) {
        }
    }

    private Object read(Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public Object get(Object obj) {
        return read(obj);
    }

    public boolean getBoolean(Object obj) {
        return (Boolean) read(obj);
    }

    public byte getByte(Object obj) {
        return ((Number) read(obj)).byteValue();
    }

    public char getChar(Object obj) {
        return (Character) read(obj);
    }

    public short getShort(Object obj) {
        return ((Number) read(obj)).shortValue();
    }

    public int getInt(Object obj) {
        Object value = read(obj);
        return value instanceof Character ? (Character) value : ((Number) value).intValue();
    }

    public long getLong(Object obj) {
        Object value = read(obj);
        return value instanceof Character ? (Character) value : ((Number) value).longValue();
    }

    public float getFloat(Object obj) {
        Object value = read(obj);
        return value instanceof Character ? (Character) value : ((Number) value).floatValue();
    }

    public double getDouble(Object obj) {
        Object value = read(obj);
        return value instanceof Character ? (Character) value : ((Number) value).doubleValue();
    }

    public void set(Object obj, Object value) {
        UnsafeReflection.setFieldValue(field, obj, value);
    }

    public void setBoolean(Object obj, boolean z) {
        set(obj, z);
    }

    public void setByte(Object obj, byte b) {
        set(obj, b);
    }

    public void setChar(Object obj, char c) {
        set(obj, c);
    }

    public void setShort(Object obj, short s) {
        set(obj, s);
    }

    public void setInt(Object obj, int i) {
        set(obj, i);
    }

    public void setLong(Object obj, long l) {
        set(obj, l);
    }

    public void setFloat(Object obj, float f) {
        set(obj, f);
    }

    public void setDouble(Object obj, double d) {
        set(obj, d);
    }
}
