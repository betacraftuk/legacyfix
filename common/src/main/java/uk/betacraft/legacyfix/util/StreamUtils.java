package uk.betacraft.legacyfix.util;

import uk.betacraft.legacyfix.Logger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class StreamUtils {
    public static byte[] readInputStream(InputStream in) {
        try {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int count;
            while ((count = in.read(buffer)) > 0) {
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (Throwable t) {
            Logger.error("readInputStream", t);
            return null;
        }
    }
}
