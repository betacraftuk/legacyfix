package uk.betacraft.legacyfix.jvm.sunreflect;

@SuppressWarnings("unused")
public interface FieldAccessor {
    Object get(Object obj) throws IllegalArgumentException;
    boolean getBoolean(Object obj) throws IllegalArgumentException;
    byte getByte(Object obj) throws IllegalArgumentException;
    char getChar(Object obj) throws IllegalArgumentException;
    short getShort(Object obj) throws IllegalArgumentException;
    int getInt(Object obj) throws IllegalArgumentException;
    long getLong(Object obj) throws IllegalArgumentException;
    float getFloat(Object obj) throws IllegalArgumentException;
    double getDouble(Object obj) throws IllegalArgumentException;
    void set(Object obj, Object value) throws IllegalArgumentException, IllegalAccessException;
    void setBoolean(Object obj, boolean z) throws IllegalArgumentException, IllegalAccessException;
    void setByte(Object obj, byte b) throws IllegalArgumentException, IllegalAccessException;
    void setChar(Object obj, char c) throws IllegalArgumentException, IllegalAccessException;
    void setShort(Object obj, short s) throws IllegalArgumentException, IllegalAccessException;
    void setInt(Object obj, int i) throws IllegalArgumentException, IllegalAccessException;
    void setLong(Object obj, long l) throws IllegalArgumentException, IllegalAccessException;
    void setFloat(Object obj, float f) throws IllegalArgumentException, IllegalAccessException;
    void setDouble(Object obj, double d) throws IllegalArgumentException, IllegalAccessException;
}
