package uk.betacraft.legacyfix.proxy.assets;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.util.web.RequestUtil;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AssetUtils {
    private static File ASSETS_DIR = null;
    private static File OVERRIDE_DIR = null;

    public static List<AssetObject> assets = new LinkedList<AssetObject>();

    private static File getAssetsDir() {
        if (ASSETS_DIR == null) {
            ASSETS_DIR = new File(GameArgs.getAssetsDir());
        }

        return ASSETS_DIR;
    }

    private static File getOverrideDir() {
        if (OVERRIDE_DIR == null) {
            OVERRIDE_DIR = new File(GameArgs.getGameDir(), "assets-override/");
            OVERRIDE_DIR.mkdirs();
        }

        return OVERRIDE_DIR;
    }

    public static JSONObject getAssetIndex() throws FileNotFoundException {
        String assetIndexPath = GameArgs.getAssetIndexPath();
        if (assetIndexPath == null) {
            return new JSONObject();
        }

        return new JSONObject(
            new JSONTokener(new InputStreamReader(new FileInputStream(assetIndexPath)))
        );
    }

    public static String generateTxtIndex() {
        try {
            JSONObject assetIndex = getAssetIndex();
            initAssets(assetIndex);

            StringBuilder txtIndex = new StringBuilder();

            for (AssetObject assetObject : assets) {
                txtIndex.append(assetObject.key).append(",").append(assetObject.size).append(",0").append("\n");
            }

            return txtIndex.toString();
        } catch (Throwable t) {
            Logger.error("generateTxtIndex", t);
            return "";
        }
    }

    public static String generateXmlIndex() {
        try {
            JSONObject assetIndex = getAssetIndex();
            initAssets(assetIndex);

            Document xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element rootElement = xmlDocument.createElement("ListBucketResult");
            xmlDocument.appendChild(rootElement);

            for (AssetObject assetObject : assets) {
                rootElement.appendChild(makeContentsNode(xmlDocument, assetObject.key, assetObject.size));
            }

            StringWriter stringWriter = new StringWriter();

            TransformerFactory.newInstance().newTransformer().transform(
                new DOMSource(xmlDocument),
                new StreamResult(stringWriter)
            );

            return stringWriter.toString();
        } catch (Throwable t) {
            Logger.error("generateXmlIndex", t);
            return "";
        }
    }

    public static Element makeContentsNode(Document xmlDocument, String key, long size) {
        Element contentsNode = xmlDocument.createElement("Contents");

        Element keyNode = xmlDocument.createElement("Key");
        keyNode.setTextContent(key);
        contentsNode.appendChild(keyNode);

        Element sizeNode = xmlDocument.createElement("Size");
        sizeNode.setTextContent(Long.toString(size));
        contentsNode.appendChild(sizeNode);

        return contentsNode;
    }

    public static List<File> recursePaths(File startingDir, List<File> list) {
        File[] files = startingDir.listFiles();
        if (files == null) {
            return list;
        }

        for (File localAsset : files) {
            try {
                list.add(localAsset.getCanonicalFile());
            } catch (IOException e) {
                Logger.error("recursePaths", e);
            }

            if (localAsset.isDirectory()) {
                recursePaths(localAsset, list);
            }
        }

        return list;
    }

    public static void initAssets(JSONObject assetIndex) {
        assets.clear();
        try {
            List<File> localAssetsToSkip = new LinkedList<File>();

            JSONObject objects = assetIndex.getJSONObject("objects");
            JSONObject custom = assetIndex.optJSONObject("custom", new JSONObject());
            for (String customObj : custom.keySet()) {
                objects.put(customObj, custom.get(customObj));
            }

            for (String key : objects.keySet()) {
                JSONObject assetObject = objects.getJSONObject(key);

                final String hash = assetObject.getString("hash");
                long size;

                // Use local file if it overrides the asset at its path
                File localAsset = new File(getOverrideDir(), key).getCanonicalFile();
                localAssetsToSkip.add(localAsset);

                final String path;
                if (localAsset.exists() && localAsset.isFile()) {
                    size = localAsset.length();
                    path = localAsset.getPath();
                } else {
                    size = assetObject.getLong("size");
                    path = new File(getAssetsDir(), "objects/" + hash.substring(0, 2) + "/" + hash).getPath();
                }

                assets.add(new AssetObject(key, size, path));
            }

            // Add the remaining (additional) local asset files
            List<File> localAssets = recursePaths(getOverrideDir(), new LinkedList<File>());
            localAssets.removeAll(localAssetsToSkip);

            for (File additionalAsset : localAssets) {
                if (additionalAsset.isDirectory()) {
                    continue;
                }

                String key = additionalAsset.getCanonicalPath().substring(
                    getOverrideDir().getCanonicalPath().length() + 1
                ).replace("\\", "/");

                if (key.indexOf('/') == -1
                    || key.startsWith("._")
                    || key.endsWith(".DS_Store")
                    || key.endsWith("Thumbs.db")
                    || key.endsWith("desktop.ini")
                ) {
                    continue;
                }

                assets.add(new AssetObject(key, additionalAsset.length(), additionalAsset.getPath()));
            }

            System.setProperty("assets-loaded", "true");
        } catch (Throwable t) {
            Logger.error("initAssets", t);
        }
    }

    public static void downloadAssets() {
        String assetIndexPath = GameArgs.getAssetIndexPath();
        if (assetIndexPath == null) {
            return;
        }

        File assetIndexFile = new File(assetIndexPath);

        JSONObject assetIndexJson;
        try {
            assetIndexJson = new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream(assetIndexFile))));
        } catch (FileNotFoundException e) {
            Logger.error("Could not read local asset index json", e);
            return;
        }

        File assetsDir = new File(GameArgs.getAssetsDir());

        JSONObject objects = assetIndexJson.optJSONObject("custom", new JSONObject());

        Set<String> assetSet = objects.keySet();
        for (String assetId : assetSet) {
            JSONObject asset = objects.getJSONObject(assetId);
            String hash = asset.getString("hash");
            String hashPath = "/" + hash.substring(0, 2) + "/" + hash;

            File assetFile = new File(assetsDir, "objects" + hashPath);
            if (assetFile.exists() && assetFile.length() == asset.getLong("size")) {
                continue;
            }

            String url;
            if (asset.has("url")) {
                url = asset.getString("url");
            } else {
                url = "https://resources.download.minecraft.net" + hashPath;
            }

            Logger.info("Downloading asset: '" + assetId + "'");
            if (!RequestUtil.download(url, assetFile)) {
                Logger.error("launcher", "Failed to download asset '" + assetId + "' from index '" + GameArgs.getAssetIndexId() + "'");
                return;
            }
        }

        Logger.info("All custom assets were downloaded for asset index '" + GameArgs.getAssetIndexId() + "'");
    }

    // Used by GameDirPatch
    // Patch for calls to Minecraft.getWorkingDirectory(String)
    public static String getRelativePathToGameDir(String path) {
        if (path.startsWith(".minecraft/")) {
            path = path.substring(".minecraft".length());
        } else if (path.startsWith("minecraft/")) {
            path = path.substring("minecraft".length());
        } else if (path.startsWith("Library/Application Support/minecraft")) {
            path = path.substring("Library/Application Support/minecraft".length());
        }

        if (path.equals("/")) {
            path = "";
        }

        return GameArgs.getGameDir() + path;
    }

    // Used by GameDirPatch
    public static String getAssetPathFromExpectedPath(String path) {
        AssetObject asset = getAssetFromExpectedPath(path);
        if (asset != null) {
            return asset.path;
        }
        return null;
    }

    // Used by GameDirPatch
    public static long getAssetSizeFromExpectedPath(String path) {
        AssetObject asset = getAssetFromExpectedPath(path);
        if (asset != null) {
            return asset.size;
        }
        return -1L;
    }

    // Used by GameDirPatch
    public static boolean isExpectedAssetsDir(String path) {
        return getExpectedAssetsDir().getPath().equals(path) || path.equals("./assets");
    }

    // Used by GameDirPatch
    public static File getCacheDirectory() {
        File file = new File(GameArgs.getGameDir(), "cache");
        file.mkdirs();
        return file;
    }

    public static File getExpectedAssetsDir() {
        if (Agent.getSetting("lf.usesWorkDir", false)) {
            return new File(GameArgs.getGameDir(), "assets");
        }
        return getAssetsDir();
    }

    // Used by GameDirPatch
    public static File[] getAssetsAsFileArray() {
        if (assets.isEmpty()) {
            try {
                initAssets(getAssetIndex());
            } catch (FileNotFoundException e) {
                Logger.error("getAssetsAsFileArray", e);
            }
        }

        List<File> listFiles = new LinkedList<File>();

        for (AssetObject asset : assets) {
            listFiles.add(new File(getExpectedAssetsDir(), asset.key));
        }

        return listFiles.toArray(new File[0]);
    }

    public static AssetObject getAssetFromExpectedPath(String path) {
        if (assets.isEmpty()) {
            try {
                initAssets(getAssetIndex());
            } catch (FileNotFoundException e) {
                Logger.error("getAssetFromExpectedPath", e);
            }
        }

        String winEscaped = path.replace("\\", "/");

        for (AssetObject asset : assets) {
            if (winEscaped.endsWith(asset.key)) {
                return asset;
            }
        }

        return null;
    }

    public static class AssetObject {
        public final String key;
        public final long size;
        public final String path;

        public AssetObject(String key, long size, String path) {
            this.key = key;
            this.size = size;
            this.path = path;

            Logger.debug("Asset: " + key + ", " + size + ", " + path);
        }
    }
}
