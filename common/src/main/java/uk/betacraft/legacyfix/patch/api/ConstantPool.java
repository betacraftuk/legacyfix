package uk.betacraft.legacyfix.patch.api;

import java.io.UnsupportedEncodingException;

public class ConstantPool {
    public static final int UTF8 = 1;
    public static final int CLASS = 7;
    public static final int METHODREF = 10;
    public static final int INTERFACE_METHODREF = 11;
    public static final int METHOD_HANDLE = 15;

    private final byte[] bytes;
    private final int[] offsets;
    private final int end;

    private ConstantPool(byte[] bytes, int[] offsets, int end) {
        this.bytes = bytes;
        this.offsets = offsets;
        this.end = end;
    }

    public static ConstantPool parse(byte[] b) {
        if (b.length < 10 || u2(b, 0) != 0xCAFE || u2(b, 2) != 0xBABE) {
            return null;
        }

        int count = u2(b, 8);
        int[] offsets = new int[count];
        int pos = 10;
        for (int i = 1; i < count; i++) {
            offsets[i] = pos;
            switch (b[pos] & 0xFF) {
                case UTF8:
                    pos += 3 + u2(b, pos + 1);
                    break;
                case 3: case 4: // Integer, Float
                    pos += 5;
                    break;
                case 5: case 6: // Long, Double, which take up two slots
                    pos += 9;
                    i++;
                    break;
                case CLASS: case 8: case 16: case 19: case 20: // String, MethodType, Module, Package
                    pos += 3;
                    break;
                case 9: case METHODREF: case INTERFACE_METHODREF: case 12: case 17: case 18: // Fieldref, NameAndType, Dynamic, InvokeDynamic
                    pos += 5;
                    break;
                case METHOD_HANDLE:
                    pos += 4;
                    break;
                default:
                    return null;
            }

            if (pos > b.length) {
                return null;
            }
        }

        return new ConstantPool(b, offsets, pos);
    }

    public int count() {
        return offsets.length;
    }

    public int offset(int i) {
        return offsets[i];
    }

    public int tag(int i) {
        return offsets[i] == 0 ? 0 : bytes[offsets[i]] & 0xFF;
    }

    public int size(int i) {
        int next = i + 1;
        while (next < offsets.length && offsets[next] == 0) {
            next++;
        }
        return (next < offsets.length ? offsets[next] : end) - offsets[i];
    }

    public int end() {
        return end;
    }

    public String utf8(int i) {
        try {
            return new String(bytes, offsets[i] + 3, u2(offsets[i] + 1), "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public String className(int classIndex) {
        return utf8(u2(offsets[classIndex] + 1));
    }

    public int u2(int pos) {
        return u2(bytes, pos);
    }

    private static int u2(byte[] b, int pos) {
        return ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
    }
}
