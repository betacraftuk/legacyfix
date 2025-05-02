package uk.betacraft.legacyfix.util;

import uk.betacraft.legacyfix.LFLogger;

import java.security.MessageDigest;

public class HashUtils {
    public static String sha1(String input) {
        try {
            MessageDigest mDigest = MessageDigest.getInstance("SHA-1");
            byte[] result = mDigest.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(Integer.toString((b & 0xFF) + 256, 16).substring(1));
            }
            return sb.toString();
        } catch (Throwable t) {
            LFLogger.error("Failed to sha1 string input", t);
            return null;
        }
    }
}
