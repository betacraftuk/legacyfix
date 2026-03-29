package uk.betacraft.legacyfix.proxy.assets;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.LinkedList;
import java.util.List;

public class AssetUtils {
    private static File ASSETS_DIR = null;
    private static File RESOURCES_DIR = null;

    public static List<AssetObject> assets = new LinkedList<AssetObject>();

    private static File getAssetsDir() {
        if (ASSETS_DIR == null) {
            ASSETS_DIR = new File(GameArgs.getAssetsDir());
        }

        return ASSETS_DIR;
    }

    private static File getResourcesDir() {
        if (RESOURCES_DIR == null) {
            RESOURCES_DIR = new File(GameArgs.getGameDir(), "resources/");
        }

        return RESOURCES_DIR;
    }

    public static JSONObject getAssetIndex() throws FileNotFoundException {
        String assetIndexPath = GameArgs.getAssetIndexPath();
        if (assetIndexPath == null) {
            return new JSONObject();
        }

        return new JSONObject(
            new JSONTokener(new InputStreamReader(new FileInputStream(assetIndexPath)))
        ).getJSONObject("objects");
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

            for (String key : assetIndex.keySet()) {
                JSONObject assetObject = assetIndex.getJSONObject(key);

                final String hash = assetObject.getString("hash");
                long size;

                // Use local file if it overrides the asset at its path
                File localAsset = new File(getResourcesDir(), key).getCanonicalFile();
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
            List<File> localAssets = recursePaths(getResourcesDir(), new LinkedList<File>());
            localAssets.removeAll(localAssetsToSkip);

            for (File additionalAsset : localAssets) {
                if (additionalAsset.isDirectory()) {
                    continue;
                }

                String key = additionalAsset.getCanonicalPath().substring(
                    getResourcesDir().getCanonicalPath().length() + 1
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
            Logger.error("getAssets", t);
        }
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
        return getExpectedAssetsDir().getPath().equals(path);
    }

    // Used by GameDirPatch
    public static File getCacheDirectory() {
        File file = new File(GameArgs.getGameDir(), "cache");
        file.mkdirs();
        return file;
    }

    public static File getExpectedAssetsDir() {
        // TODO 13w16a-13w23b
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
