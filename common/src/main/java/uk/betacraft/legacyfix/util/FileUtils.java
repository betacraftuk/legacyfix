package uk.betacraft.legacyfix.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class FileUtils {
    public static void removeRecursively(File dir, boolean deleteFolderItself, boolean deleteOnlyFiles) {
        if (!dir.exists()) {
            return;
        }

        String[] entries = dir.list();
        if (entries == null) {
            if (deleteFolderItself) {
                dir.delete();
            }

            return;
        }

        for (String s : entries) {
            File file = new File(dir.getPath(), s);
            if (file.isDirectory() && !deleteOnlyFiles) {
                removeRecursively(file, true, false);
            } else {
                file.delete();
            }
        }

        if (deleteFolderItself) {
            dir.delete();
        }
    }

    public static String sha1OfFile(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(f));
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception ignored) {}
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            int v = b & 0xff;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }
}
