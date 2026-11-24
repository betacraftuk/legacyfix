package uk.betacraft.legacyfix.jvm;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

/**
 * Pack200 unpacker for archives of resource files (Forge's binpatches on Java 14+).
 * Probably shouldn't be used anywhere else
 */
public class ResourceUnpacker {
    private static final int
        HAVE_SPECIAL_FORMATS = 1,
        HAVE_CP_NUMBERS = 1 << 1,
        HAVE_CP_EXTRAS = 1 << 3,
        HAVE_FILE_HEADERS = 1 << 4,
        DEFLATE_HINT = 1 << 5,
        HAVE_FILE_MODTIME = 1 << 6,
        HAVE_FILE_OPTIONS = 1 << 7,
        HAVE_FILE_SIZE_HI = 1 << 8;

    private static final Bhsd
        BYTE1 = new Bhsd(1, 256, 0, 0),
        CHAR3 = new Bhsd(3, 128, 0, 0),
        UNSIGNED5 = new Bhsd(5, 64, 0, 0),
        DELTA5 = new Bhsd(5, 64, 1, 1);

    private static final int[][] CANONICAL = {
        null,
        {1, 256, 0, 0}, {1, 256, 1, 0}, {1, 256, 0, 1}, {1, 256, 1, 1},
        {2, 256, 0, 0}, {2, 256, 1, 0}, {2, 256, 0, 1}, {2, 256, 1, 1},
        {3, 256, 0, 0}, {3, 256, 1, 0}, {3, 256, 0, 1}, {3, 256, 1, 1},
        {4, 256, 0, 0}, {4, 256, 1, 0}, {4, 256, 0, 1}, {4, 256, 1, 1},
        {5, 4, 0, 0}, {5, 4, 1, 0}, {5, 4, 2, 0},
        {5, 16, 0, 0}, {5, 16, 1, 0}, {5, 16, 2, 0},
        {5, 32, 0, 0}, {5, 32, 1, 0}, {5, 32, 2, 0},
        {5, 64, 0, 0}, {5, 64, 1, 0}, {5, 64, 2, 0},
        {5, 128, 0, 0}, {5, 128, 1, 0}, {5, 128, 2, 0},
        {5, 4, 0, 1}, {5, 4, 1, 1}, {5, 4, 2, 1},
        {5, 16, 0, 1}, {5, 16, 1, 1}, {5, 16, 2, 1},
        {5, 32, 0, 1}, {5, 32, 1, 1}, {5, 32, 2, 1},
        {5, 64, 0, 1}, {5, 64, 1, 1}, {5, 64, 2, 1},
        {5, 128, 0, 1}, {5, 128, 1, 1}, {5, 128, 2, 1},
        {2, 192, 0, 0}, {2, 224, 0, 0}, {2, 240, 0, 0}, {2, 248, 0, 0}, {2, 252, 0, 0},
        {2, 8, 0, 1}, {2, 8, 1, 1}, {2, 16, 0, 1}, {2, 16, 1, 1}, {2, 32, 0, 1}, {2, 32, 1, 1},
        {2, 64, 0, 1}, {2, 64, 1, 1}, {2, 128, 0, 1}, {2, 128, 1, 1}, {2, 192, 0, 1}, {2, 192, 1, 1},
        {2, 224, 0, 1}, {2, 224, 1, 1}, {2, 240, 0, 1}, {2, 240, 1, 1}, {2, 248, 0, 1}, {2, 248, 1, 1},
        {3, 192, 0, 0}, {3, 224, 0, 0}, {3, 240, 0, 0}, {3, 248, 0, 0}, {3, 252, 0, 0},
        {3, 8, 0, 1}, {3, 8, 1, 1}, {3, 16, 0, 1}, {3, 16, 1, 1}, {3, 32, 0, 1}, {3, 32, 1, 1},
        {3, 64, 0, 1}, {3, 64, 1, 1}, {3, 128, 0, 1}, {3, 128, 1, 1}, {3, 192, 0, 1}, {3, 192, 1, 1},
        {3, 224, 0, 1}, {3, 224, 1, 1}, {3, 240, 0, 1}, {3, 240, 1, 1}, {3, 248, 0, 1}, {3, 248, 1, 1},
        {4, 192, 0, 0}, {4, 224, 0, 0}, {4, 240, 0, 0}, {4, 248, 0, 0}, {4, 252, 0, 0},
        {4, 8, 0, 1}, {4, 8, 1, 1}, {4, 16, 0, 1}, {4, 16, 1, 1}, {4, 32, 0, 1}, {4, 32, 1, 1},
        {4, 64, 0, 1}, {4, 64, 1, 1}, {4, 128, 0, 1}, {4, 128, 1, 1}, {4, 192, 0, 1}, {4, 192, 1, 1},
        {4, 224, 0, 1}, {4, 224, 1, 1}, {4, 240, 0, 1}, {4, 240, 1, 1}, {4, 248, 0, 1}, {4, 248, 1, 1}
    };

    private final InputStream in;
    private InputStream bandHeaders = new ByteArrayInputStream(new byte[0]);

    private ResourceUnpacker(InputStream in) {
        this.in = in;
    }

    public static void unpackSegment(InputStream in, JarOutputStream out, Boolean deflateOverride) throws IOException {
        new ResourceUnpacker(in).unpack(out, deflateOverride);
    }

    private void unpack(JarOutputStream out, Boolean deflateOverride) throws IOException {
        int magic = (read() << 24) | (read() << 16) | (read() << 8) | read();
        if (magic != 0xCAFED00D) {
            throw new IOException("Not a Pack200 archive (magic " + Integer.toHexString(magic) + ")");
        }

        int minver = UNSIGNED5.decode(in, 0);
        int majver = UNSIGNED5.decode(in, 0);
        int options = UNSIGNED5.decode(in, 0);
        if (majver < 150 || majver > 171) {
            throw new IOException("Unsupported Pack200 version " + majver + "." + minver);
        }

        long archiveModtime = 0;
        int fileCount = 0;
        if ((options & HAVE_FILE_HEADERS) != 0) {
            UNSIGNED5.decode(in, 0); // archive_size_hi
            UNSIGNED5.decode(in, 0); // archive_size_lo
            UNSIGNED5.decode(in, 0); // archive_next_count
            archiveModtime = UNSIGNED5.decode(in, 0);
            fileCount = UNSIGNED5.decode(in, 0);
        }

        int bandHeadersSize = 0;
        if ((options & HAVE_SPECIAL_FORMATS) != 0) {
            bandHeadersSize = UNSIGNED5.decode(in, 0);
            expectZero("attr_definition_count", UNSIGNED5.decode(in, 0));
        }

        int utf8Count = UNSIGNED5.decode(in, 0);
        int otherCpCount = 7; // String, Class, Signature, Descr, Field, Method, IMethod
        if ((options & HAVE_CP_NUMBERS) != 0) {
            otherCpCount += 4; // Int, Float, Long, Double
        }
        if ((options & HAVE_CP_EXTRAS) != 0) {
            otherCpCount += 4; // MethodHandle, MethodType, BootstrapMethod, InvokeDynamic
        }
        for (int i = 0; i < otherCpCount; i++) {
            expectZero("non-Utf8 constant pool count", UNSIGNED5.decode(in, 0));
        }

        expectZero("ic_count", UNSIGNED5.decode(in, 0));
        UNSIGNED5.decode(in, 0); // default_class_minver
        UNSIGNED5.decode(in, 0); // default_class_majver
        expectZero("class_count", UNSIGNED5.decode(in, 0));

        byte[] headers = new byte[bandHeadersSize];
        readFully(headers);
        bandHeaders = new ByteArrayInputStream(headers);

        String[] utf8 = readUtf8(utf8Count);

        int[] fileName = band(UNSIGNED5, fileCount);
        int[] fileSizeHi = (options & HAVE_FILE_SIZE_HI) != 0 ? band(UNSIGNED5, fileCount) : new int[fileCount];
        int[] fileSizeLo = band(UNSIGNED5, fileCount);
        int[] fileModtime = (options & HAVE_FILE_MODTIME) != 0 ? band(DELTA5, fileCount) : new int[fileCount];
        int[] fileOptions = (options & HAVE_FILE_OPTIONS) != 0 ? band(UNSIGNED5, fileCount) : new int[fileCount];

        for (int i = 0; i < fileCount; i++) {
            if ((fileOptions[i] & 2) != 0) {
                throw new IOException("Pack200 archives with class files aren't supported");
            }
            if (fileName[i] < 0 || fileName[i] >= utf8.length) {
                throw new IOException("Bad file name reference " + fileName[i]);
            }
            long size = ((long) fileSizeHi[i] << 32) | (fileSizeLo[i] & 0xFFFFFFFFL);
            if (size > Integer.MAX_VALUE) {
                throw new IOException("File too large: " + utf8[fileName[i]]);
            }

            byte[] data = new byte[(int) size];
            readFully(data);

            boolean deflate = deflateOverride != null
                ? deflateOverride
                : (fileOptions[i] & 1) != 0 || (options & DEFLATE_HINT) != 0;

            JarEntry entry = new JarEntry(utf8[fileName[i]]);
            entry.setTime(1000L * (archiveModtime + fileModtime[i]));
            if (deflate) {
                entry.setMethod(ZipEntry.DEFLATED);
            } else {
                CRC32 crc = new CRC32();
                crc.update(data);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(data.length);
                entry.setCompressedSize(data.length);
                entry.setCrc(crc.getValue());
            }
            out.putNextEntry(entry);
            out.write(data);
            out.closeEntry();
        }
    }

    private String[] readUtf8(int count) throws IOException {
        if (count == 0) {
            return new String[0];
        }

        int[] prefix = band(DELTA5, Math.max(0, count - 2));
        int[] suffix = band(UNSIGNED5, count - 1);

        int charCount = 0;
        int bigCount = 0;
        for (int length : suffix) {
            if (length == 0) {
                bigCount++;
            } else {
                charCount += length;
            }
        }

        int[] chars = band(CHAR3, charCount);
        int[] bigSuffix = band(DELTA5, bigCount);
        int[][] bigChars = new int[bigCount][];
        for (int i = 0; i < bigCount; i++) {
            bigChars[i] = band(DELTA5, bigSuffix[i]);
        }

        String[] strings = new String[count];
        strings[0] = "";
        int charPos = 0;
        int bigPos = 0;
        for (int i = 1; i < count; i++) {
            StringBuilder sb = new StringBuilder(strings[i - 1].substring(0, i > 1 ? prefix[i - 2] : 0));
            if (suffix[i - 1] == 0) {
                for (int c : bigChars[bigPos++]) {
                    sb.append((char) c);
                }
            } else {
                for (int j = 0; j < suffix[i - 1]; j++) {
                    sb.append((char) chars[charPos++]);
                }
            }
            strings[i] = sb.toString();
        }
        return strings;
    }

    private int[] band(Bhsd defaultCodec, int count) throws IOException {
        if (count == 0 || defaultCodec.b == 1) {
            return defaultCodec.decodeInts(in, count, null);
        }

        int first = defaultCodec.decodeInts(in, 1, null)[0];
        int escape = -1;
        if (defaultCodec.s != 0 && first >= -256 && first <= -1) {
            escape = -1 - first;
        } else if (defaultCodec.s == 0 && first >= defaultCodec.l && first <= defaultCodec.l + 255) {
            escape = first - defaultCodec.l;
        }

        if (escape == -1) {
            return defaultCodec.decodeInts(in, count, first);
        }

        Codec codec = codec(escape, defaultCodec);
        int[] values = codec.decodeInts(in, count, null);
        if (codec instanceof Population) {
            ((Population) codec).normalise(values);
        }
        return values;
    }

    private Codec codec(int value, Bhsd defaultCodec) throws IOException {
        if (value == 0) {
            return defaultCodec;
        }
        if (value <= 115) {
            int[] c = CANONICAL[value];
            return new Bhsd(c[0], c[1], c[2], c[3]);
        }
        if (value == 116) {
            int code = headerByte();
            int d = code & 1;
            int s = (code >> 1) & 3;
            int b = ((code >> 3) & 7) + 1;
            int h = headerByte() + 1;
            return new Bhsd(b, h, s, d);
        }
        if (value <= 140) {
            int offset = value - 117;
            int kx = offset & 3;
            boolean kbFlag = ((offset >> 2) & 1) != 0;
            boolean aDef = ((offset >> 3) & 1) != 0;
            boolean bDef = ((offset >> 4) & 1) != 0;
            if (aDef && bDef) {
                throw new IOException("Bad run coding, ADef and BDef both set?");
            }
            int kb = kbFlag ? headerByte() : 3;
            int k = (kb + 1) << (4 * kx);
            Codec a = aDef ? defaultCodec : codec(headerByte(), defaultCodec);
            Codec b = bDef ? defaultCodec : codec(headerByte(), defaultCodec);
            return new Run(k, a, b);
        }
        if (value <= 188) {
            int offset = value - 141;
            boolean fDef = (offset & 1) != 0;
            boolean uDef = ((offset >> 1) & 1) != 0;
            int tDefL = offset >> 2;
            int[] tDefToL = {0, 4, 8, 16, 32, 64, 128, 192, 224, 240, 248, 252};

            // F, T, U must be read in this order
            Codec f = fDef ? defaultCodec : codec(headerByte(), defaultCodec);
            Codec t = null;
            if (tDefL == 0) {
                t = codec(headerByte(), defaultCodec);
            }
            Codec u = uDef ? defaultCodec : codec(headerByte(), defaultCodec);
            return new Population(bhsd(f), t, tDefToL[tDefL], bhsd(u));
        }
        throw new IOException("Invalid coding specifier " + value);
    }

    private static Bhsd bhsd(Codec codec) throws IOException {
        if (!(codec instanceof Bhsd)) {
            throw new IOException("Nested run/population codings aren't supported");
        }
        return (Bhsd) codec;
    }

    private int headerByte() throws IOException {
        int b = bandHeaders.read();
        if (b == -1) {
            throw new EOFException("Ran out of band headers");
        }
        return b;
    }

    private int read() throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new EOFException();
        }
        return b;
    }

    private void readFully(byte[] data) throws IOException {
        int off = 0;
        while (off < data.length) {
            int r = in.read(data, off, data.length - off);
            if (r == -1) {
                throw new EOFException();
            }
            off += r;
        }
    }

    private static void expectZero(String what, int value) throws IOException {
        if (value != 0) {
            throw new IOException("Unsupported Pack200 archive (" + what + " is " + value + ")");
        }
    }

    private static abstract class Codec {
        abstract int[] decodeInts(InputStream in, int n, Integer first) throws IOException;
    }

    private static class Bhsd extends Codec {
        final int b, h, s, d, l;
        final long cardinality, smallest, largest;
        final long[] powers;

        Bhsd(int b, int h, int s, int d) {
            this.b = b;
            this.h = h;
            this.s = s;
            this.d = d;
            this.l = 256 - h;
            this.powers = new long[b];

            long card = 0;
            long p = 1;
            for (int i = 0; i < b; i++) {
                powers[i] = p;
                card += l * p;
                p *= h;
            }
            this.cardinality = card + p;

            this.smallest = cardinality >= 0x100000000L ? Integer.MIN_VALUE : 0;
            this.largest = Math.min(((long) Integer.MAX_VALUE << 1) - 1, cardinality - 1);
        }

        int decode(InputStream in, int last) throws IOException {
            long z = 0;
            int n = 0;
            int x;
            do {
                x = in.read();
                if (x == -1) {
                    throw new EOFException("EOS while decoding a band");
                }
                z += x * powers[n];
                n++;
            } while (x >= l && n < b);

            if (s != 0) {
                long u = (1L << s) - 1;
                if ((z & u) == u) {
                    z = ~(z >>> s);
                } else {
                    z = z - (z >>> s);
                }
            }
            if (d != 0) {
                z += last;
            }
            return (int) z;
        }

        int[] decodeInts(InputStream in, int n, Integer first) throws IOException {
            int[] values = new int[n];
            int i = 0;
            int last = 0;
            if (first != null && n > 0) {
                values[i++] = last = first;
            }
            for (; i < n; i++) {
                values[i] = last = decode(in, last);
            }
            if (d != 0) {
                for (int j = 0; j < n; j++) {
                    values[j] = normalise(values[j]);
                }
            }
            return values;
        }

        int normalise(int value) {
            long v = value;
            while (v > largest) {
                v -= cardinality;
            }
            while (v < smallest) {
                v += cardinality;
            }
            return (int) v;
        }
    }

    private static class Run extends Codec {
        private final int k;
        private final Codec a, b;

        Run(int k, Codec a, Codec b) {
            this.k = k;
            this.a = a;
            this.b = b;
        }

        int[] decodeInts(InputStream in, int n, Integer first) throws IOException {
            int aCount = Math.min(k, n);
            int[] aValues = a.decodeInts(in, aCount, null);
            int[] bValues = b.decodeInts(in, n - aCount, null);
            int[] values = new int[n];
            System.arraycopy(aValues, 0, values, 0, aCount);
            System.arraycopy(bValues, 0, values, aCount, n - aCount);
            return values;
        }
    }

    private static class Population extends Codec {
        private final Bhsd favouredCodec, unfavouredCodec;
        private final int l;
        private Codec tokenCodec;
        private int[] favoured;

        Population(Bhsd favouredCodec, Codec tokenCodec, int l, Bhsd unfavouredCodec) {
            this.favouredCodec = favouredCodec;
            this.tokenCodec = tokenCodec;
            this.l = l;
            this.unfavouredCodec = unfavouredCodec;
        }

        int[] decodeInts(InputStream in, int n, Integer first) throws IOException {
            int[] table = new int[16];
            int smallest = Integer.MAX_VALUE;
            int last = 0;
            int k = -1;
            while (true) {
                int value = favouredCodec.decode(in, last);
                if (k > -1 && (value == smallest || value == last)) {
                    break;
                }
                if (++k == table.length) {
                    int[] grown = new int[table.length * 2];
                    System.arraycopy(table, 0, grown, 0, table.length);
                    table = grown;
                }
                table[k] = value;
                int absSmallest = Math.abs(smallest);
                int absValue = Math.abs(value);
                if (absSmallest > absValue) {
                    smallest = value;
                } else if (absSmallest == absValue) {
                    smallest = absSmallest;
                }
                last = value;
            }
            favoured = new int[k + 1];
            System.arraycopy(table, 0, favoured, 0, k + 1);

            if (tokenCodec == null) {
                if (k < 256) {
                    tokenCodec = BYTE1;
                } else {
                    for (int b = 2; b < 5 && tokenCodec == null; b++) {
                        Bhsd candidate = new Bhsd(b, 256 - l, 0, 0);
                        if (k <= candidate.largest) {
                            tokenCodec = candidate;
                        }
                    }
                    if (tokenCodec == null) {
                        throw new IOException("Can't derive population token coding for K=" + k + ", L=" + l);
                    }
                }
            }

            int[] values = tokenCodec.decodeInts(in, n, null);
            last = 0;
            for (int i = 0; i < n; i++) {
                int index = values[i];
                if (index == 0) {
                    values[i] = last = unfavouredCodec.decode(in, last);
                } else {
                    values[i] = favoured[index - 1];
                }
            }
            return values;
        }

        void normalise(int[] values) {
            int[] sorted = favoured.clone();
            Arrays.sort(sorted);
            for (int i = 0; i < values.length; i++) {
                Bhsd codec = Arrays.binarySearch(sorted, values[i]) >= 0 ? favouredCodec : unfavouredCodec;
                if (codec.d != 0) {
                    values[i] = codec.normalise(values[i]);
                }
            }
        }
    }
}
