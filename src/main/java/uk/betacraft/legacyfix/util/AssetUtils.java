package uk.betacraft.legacyfix.util;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.LinkedList;
import java.util.List;

public class AssetUtils {
    private static final String WORKING_DIR = System.getProperty("user.home");
    private static final File RESOURCES_DIR = new File(WORKING_DIR, "minecraft/resources/");

    public static JSONObject getAssetIndex() throws FileNotFoundException {
        return new JSONObject(new JSONTokener(new InputStreamReader(new FileInputStream((String) LegacyFixAgent.getSettings().get("lf.assetIndex"))))).getJSONObject("objects");
    }

    public static String generateTxtIndex() {
        try {
            StringBuilder txtIndex = new StringBuilder();

            JSONObject assetIndex = getAssetIndex();

            final List<AssetObject> assets = getAssets(assetIndex);

            for (AssetObject assetObject : assets) {
                txtIndex.append("\n").append(assetObject.key).append(",").append(assetObject.size).append(",0");
            }

            return txtIndex.substring(1);
        } catch (Throwable t) {
            LFLogger.error("AssetUtils", t);
            return "";
        }
    }

    public static String generateXmlIndex() {
        try {
            Document xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element rootElement = xmlDocument.createElement("ListBucketResult");
            xmlDocument.appendChild(rootElement);

            JSONObject assetIndex = getAssetIndex();

            final List<AssetObject> assets = getAssets(assetIndex);

            for (AssetObject assetObject : assets) {
                rootElement.appendChild(makeContentsNode(xmlDocument, assetObject.key, assetObject.size));
            }

            StringWriter stringWriter = new StringWriter();

            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(xmlDocument), new StreamResult(stringWriter));

            return stringWriter.toString();
        } catch (Throwable t) {
            LFLogger.error("AssetUtils", t);
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
        if (files == null)
            return list;

        for (File localAsset : files) {
            try {
                list.add(localAsset.getCanonicalFile());
            } catch (IOException e) {
                LFLogger.error("AssetUtils", e);
            }

            if (localAsset.isDirectory())
                recursePaths(localAsset, list);
        }

        return list;
    }

    public static List<AssetObject> getAssets(JSONObject assetIndex) {
        List<AssetObject> assets = new LinkedList<AssetObject>();
        try {
            List<File> localAssetsToSkip = new LinkedList<File>();

            for (String key : assetIndex.keySet()) {
                JSONObject assetObject = assetIndex.getJSONObject(key);

                long size;

                // Use local file if it overrides the asset at its path
                File localAsset = new File(RESOURCES_DIR, key).getCanonicalFile();

                localAssetsToSkip.add(localAsset);

                if (localAsset.exists() && localAsset.isFile())
                    size = localAsset.length();
                else
                    size = assetObject.getLong("size");

                assets.add(new AssetObject(key, size));
            }

            // Add the remaining (additional) local asset files
            List<File> localAssets = recursePaths(RESOURCES_DIR, new LinkedList<File>());

            localAssets.removeAll(localAssetsToSkip);

            for (File additionalAsset : localAssets) {

                String key = additionalAsset.getCanonicalPath().substring(RESOURCES_DIR.getCanonicalPath().length());

                assets.add(new AssetObject(key, additionalAsset.length()));
            }
        } catch (Throwable t) {
            LFLogger.error("AssetUtils", t);
        }

        return assets;
    }

    public static class AssetObject {
        public final String key;
        public final long size;

        public AssetObject(String key, long size) {
            this.key = key;
            this.size = size;
        }
    }
}
