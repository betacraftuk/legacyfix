package uk.betacraft.legacyfix.jvm;

import org.apache.commons.compress.harmony.unpack200.Segment;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarOutputStream;
import java.util.zip.GZIPInputStream;

@SuppressWarnings("unused")
public class Pack200 {
    private Pack200() {
    }

    public static Unpacker newUnpacker() {
        return new HarmonyUnpacker();
    }

    public interface Unpacker {
        String KEEP = "keep";
        String TRUE = "true";
        String FALSE = "false";
        String DEFLATE_HINT = "unpack.deflate.hint";
        String PROGRESS = "unpack.progress";

        SortedMap<String, String> properties();

        void unpack(InputStream in, JarOutputStream out) throws IOException;

        void unpack(File in, JarOutputStream out) throws IOException;
    }

    private static class HarmonyUnpacker implements Unpacker {
        private final SortedMap<String, String> properties = new TreeMap<String, String>();

        public SortedMap<String, String> properties() {
            return properties;
        }

        public void unpack(InputStream in, JarOutputStream out) throws IOException {
            if (in == null || out == null) {
                throw new IllegalArgumentException("Streams must not be null");
            }

            try {
                InputStream data = new ByteArrayInputStream(readFully(in));
                if (isGzip(data)) {
                    data = new ByteArrayInputStream(readFully(new GZIPInputStream(data)));
                }

                String deflateHint = properties.get(DEFLATE_HINT);
                boolean overrideDeflateHint = TRUE.equals(deflateHint) || FALSE.equals(deflateHint);

                out.setComment("PACK200");
                while (available(data)) {
                    Segment segment = new Segment();
                    segment.setLogLevel(Segment.LOG_LEVEL_QUIET);
                    segment.setLogStream(new DiscardingOutputStream());
                    segment.setPreRead(false);
                    if (overrideDeflateHint) {
                        segment.overrideDeflateHint(TRUE.equals(deflateHint));
                    }
                    segment.unpack(data, out);
                    out.flush();
                }

                // forge reads the jar bytes without closing or finishing the stream (thanks forge)
                out.finish();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                IOException wrapped = new IOException("Failed to unpack Pack200 archive: " + e);
                wrapped.initCause(e);
                throw wrapped;
            } finally {
                in.close();
            }
        }

        private static boolean isGzip(InputStream in) throws IOException {
            in.mark(2);
            int first = in.read();
            int second = in.read();
            in.reset();
            return first == 0x1f && second == 0x8b;
        }

        private static boolean available(InputStream in) throws IOException {
            in.mark(1);
            int next = in.read();
            in.reset();
            return next != -1;
        }

        private static byte[] readFully(InputStream in) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        }

        public void unpack(File in, JarOutputStream out) throws IOException {
            if (in == null || out == null) {
                throw new IllegalArgumentException("Arguments must not be null");
            }

            unpack(new BufferedInputStream(new FileInputStream(in), 8192), out);
        }
    }

    private static class DiscardingOutputStream extends OutputStream {
        public void write(int b) {
        }

        public void write(byte[] b, int off, int len) {
        }
    }
}
