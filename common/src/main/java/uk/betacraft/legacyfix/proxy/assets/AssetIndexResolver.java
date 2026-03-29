package uk.betacraft.legacyfix.proxy.assets;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.util.web.RequestUtil;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AssetIndexResolver {
    private static final List<Pattern> patterns = new ArrayList<Pattern>();
    private static final List<String> assetIds = new ArrayList<String>();
    private static JSONObject assetIndexesRoot = null;
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        InputStream in = null;
        try {
            in = AssetIndexResolver.class.getResourceAsStream("/version_data.json");
            if (in == null) {
                throw new RuntimeException("Missing version_data.json");
            }

            JSONArray arr = new JSONArray(new JSONTokener(in));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String regex = o.getString("version");
                Pattern p = Pattern.compile(regex);
                patterns.add(p);
                assetIds.add(o.optString("assetIndex", null));
            }
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {}
        }

        in = null;
        try {
            in = AssetIndexResolver.class.getResourceAsStream("/asset_indexes.json");
            if (in == null) {
                throw new RuntimeException("Missing asset_indexes.json");
            }

            assetIndexesRoot = new JSONObject(new JSONTokener(in));
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {}
        }

        initialized = true;
    }

    public static String resolve(String version) {
        if (assetIndexesRoot == null) {
            init();
        }

        for (int i = 0; i < patterns.size(); i++) {
            Pattern p = patterns.get(i);
            if (p.matcher(version).matches()) {
                return assetIds.get(i);
            }
        }

        return null;
    }

    public static File ensureAssetIndex(String id, File targetFile) throws Exception {
        if (assetIndexesRoot == null) {
            init();
        }

        JSONObject entry = assetIndexesRoot.optJSONObject(id);
        if (entry == null) {
            throw new RuntimeException("No asset index entry for " + id);
        }

        String url = entry.optString("url", null);
        String sha1 = entry.optString("sha1", "").trim();

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (targetFile.exists() && sha1.length() > 0) {
            String present = sha1OfFile(targetFile);
            if (sha1.equalsIgnoreCase(present)) {
                return targetFile;
            }

            targetFile.delete();
        } else if (targetFile.exists() && sha1.length() == 0) {
            return targetFile;
        }

        Logger.info("Downloading the asset index (" + id + ")...");
        if (!RequestUtil.download(url, targetFile)) {
            throw new RuntimeException("Failed to download the asset index (" + id + ")");
        }
        Logger.info("Download finished");

        if (sha1.length() > 0) {
            String got = sha1OfFile(targetFile);
            if (!sha1.equalsIgnoreCase(got)) {
                targetFile.delete();
                throw new RuntimeException(
                    "SHA-1 mismatch for downloaded asset index (" + id + "): expected " + sha1 + ", got " + got
                );
            }
        }

        return targetFile;
    }

    private static String sha1OfFile(File f) throws Exception {
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
