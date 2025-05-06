package uk.betacraft.legacyfix.util;

import java.io.File;

public class FileUtils {
    public static void removeRecursively(File dir, boolean deleteFolderItself, boolean deleteOnlyFiles) {
        if (!dir.exists())
            return;

        String[] entries = dir.list();
        if (entries == null) {
            if (deleteFolderItself)
                dir.delete();

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

        if (deleteFolderItself)
            dir.delete();
    }
}
