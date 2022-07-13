package legacyfix.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class AssetIndexUtils {

	public static final String assetDir = System.getProperty("lf.assetDir");
	public static final String assetIndex = System.getProperty("lf.assetIndex");
	public static String gameDir = System.getProperty("lf.gameDir");
	public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public static File getAssetIndex() {
		if (assetIndex == null) return null;

		File file = new File(assetIndex);
		if (!file.exists() || !file.isFile()) return null;

		return file;
	}


	public static boolean loadAssetObjectData() {
		try {
			FileInputStream fis = new FileInputStream(getAssetIndex());
			assetindex = gson.fromJson(new InputStreamReader(fis, "UTF-8"), JsonObject.class);
		} catch (Throwable t) {
			t.printStackTrace();
			return false;
		}
		return true;
	}
//
	public static JsonObject assetindex;
//	private static String xml = null;
	public static ArrayList<URI> uris = null;
	public static HashMap<String, String> namePathToHashPath = new HashMap<String, String>();
//	private static String text = null;
//
//	// Unnecessary
//	@Deprecated
//	public static String asXML() {
//		if (xml != null) return xml;
//		try {
//			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
//			Element root = doc.createElement("ListBucketResult");
//			doc.appendChild(root);
//
//			// load the assetindex json
//			JsonObject obj = assetindex.get("objects").getAsJsonObject();
//
//			for (Entry<String, JsonElement> entry : obj.entrySet()) {
//				if (!entry.getKey().contains("/")) { // out of bounds -1
//					continue;
//				}
//				JsonObject value = entry.getValue().getAsJsonObject();
//				long size = value.get("size").getAsLong();
//
//				// local sound files have priority over the default ones
//				File localfile = new File(ProxyStarter.resourcesDir, entry.getKey());
//				if (localfile.exists() && !localfile.isDirectory()) {
//					size = localfile.length();
//				}
//
//				// create resource node
//				Element contents = doc.createElement("Contents");
//
//				Element keynode = doc.createElement("Key");
//				keynode.setTextContent(entry.getKey());
//				contents.appendChild(keynode);
//
//				Element sizenode = doc.createElement("Size");
//				sizenode.setTextContent(Long.toString(size));
//				contents.appendChild(sizenode);
//
//				// add resource to root
//				root.appendChild(contents);
//			}
//			TransformerFactory transformerFactory = TransformerFactory.newInstance();
//		    Transformer transformer = transformerFactory.newTransformer();
//		    DOMSource source = new DOMSource(doc);
//		    StringWriter writer = new StringWriter();
//		    StreamResult result = new StreamResult(writer);
//		    transformer.transform(source, result);
//
//		    xml = writer.toString();
//			return xml;
//		} catch (Throwable t) {
//			t.printStackTrace();
//		}
//		return null;
//	}
//
//	public static String asText() {
//		if (text != null) return text;
//		try {
//			// load the assetindex json
//			JsonObject obj = assetindex.get("objects").getAsJsonObject();
//
//			StringBuilder strb = new StringBuilder();
//
//			ArrayList<File> local_resources = fileTree(ProxyStarter.resourcesDir);
//			for (File resource : local_resources) {
//				strb.append("\n" + ProxyStarter.resourcesDir.toURI().relativize(resource.toURI()).toString() + "," + resource.length() + "," + System.currentTimeMillis());
//			}
//
//			for (Entry<String, JsonElement> entry : obj.entrySet()) {
//				if (!entry.getKey().contains("/")) {
//					continue;
//				}
//				JsonObject value = entry.getValue().getAsJsonObject();
//				long size = value.get("size").getAsLong();
//
//				// local sound files have priority over the default ones
//				File localfile = new File(ProxyStarter.resourcesDir, entry.getKey());
//				if (localfile.exists() && !localfile.isDirectory()) {
//					continue; // continue instead, local resources are handled in the loop above
//					//size = localfile.length();
//				}
//
//				strb.append("\n" + entry.getKey() + "," + size + "," + System.currentTimeMillis());
//			}
//
//			text = strb.toString().substring(1);
//			return text;
//		} catch (Throwable t) {
//			t.printStackTrace();
//		}
//		return null;
//	}
//
	public static ArrayList<File> asFileArray() {
		ArrayList<File> list = new ArrayList<File>();
		try {
			for (URI uri : asURIs()) {
				list.add(new File(uri));
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return list;
	}

//
//	public static ArrayList<File> fileTree(File file) {
//		ArrayList<File> ret = new ArrayList<File>();
//		for (File f : file.listFiles()) {
//			if (f.isDirectory()) {
//				ret.addAll(fileTree(f));
//			} else {
//				ret.add(f);
//			}
//		}
//		return ret;
//	}

	public static void unpackMissingAssets(File assetFile, String entry) {
		try {
			InputStream is = AssetIndexUtils.class.getResourceAsStream("/patchasset/" + entry);
			FileOutputStream fout = new FileOutputStream(assetFile);
			byte[] buff = new byte[4096];
			int read;
			while (true) {
				read = is.read(buff);
				if (read <= 0) {
					break;
				}
				fout.write(buff, 0, read);
			}
			is.close();
			fout.close();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static ArrayList<URI> asURIs() {
		ArrayList<URI> uris = new ArrayList<URI>();

		if (AssetIndexUtils.assetindex == null && !AssetIndexUtils.loadAssetObjectData()) {
			return uris;
		}

		if (AssetIndexUtils.uris != null) { // cache pls
			return AssetIndexUtils.uris;
		}

		// make sure gameDir is alright
		if (gameDir.endsWith("/") || gameDir.endsWith("\\")) {
			gameDir = gameDir.substring(0, gameDir.length()-1);
		}

		// load the assetindex json
		JsonObject obj = assetindex.get("objects").getAsJsonObject();

		for (Entry<String, JsonElement> entry : obj.entrySet()) {
			if (!entry.getKey().contains("/")) { // out of bounds -1
				continue;
			}
			JsonObject value = entry.getValue().getAsJsonObject();
			String hash = value.get("hash").getAsString();

			File assetFile = new File(assetDir, "objects/" + hash.substring(0, 2) + "/" + hash);
			File assetKeyFile = new File(assetDir, entry.getKey());

			// THIS PART OF CODE IS MEANT FOR ASSETS THAT CAN'T BE APPLIED VIA THE NORMAL WAY.
			// They need to be downloaded as a jar library, and then extracted by this code.
			// Thanks mojang!!!!!!!!!!!!!!!!!!!

			// asset doesn't exist, is empty, or its hash is different (how?)
			if (!assetFile.exists() || assetFile.length() == 0 || !hash.equalsIgnoreCase(getSHA1(assetFile))) {
				assetFile.getParentFile().mkdirs();
				// extract the asset file to its dedicated hashpath
				unpackMissingAssets(assetFile, entry.getKey());
			}
			
			System.out.println(entry.getKey() + ", " + hash);

			// local sound files have priority over the default ones
			File localfile = new File(gameDir, "/resources/" + entry.getKey());
			if (localfile.exists() && !localfile.isDirectory()) {
				uris.add(localfile.toURI());
			} else {
				uris.add(assetKeyFile.toURI());
				// makes it easier to access hashpaths
				namePathToHashPath.put(entry.getKey(), assetFile.getAbsolutePath());
			}
		}
		AssetIndexUtils.uris = uris;
		return uris;
	}

	public static boolean isInURIs(URI uri) {
		if (AssetIndexUtils.uris == null) return false;

		for (URI uri1 : uris) {
			if (uri1.getPath().equals(uri.getPath())) {
				return true;
			}
		}
		return false;
	}

	public static String getSHA1(File file) {
		try {
			InputStream fis =  new FileInputStream(file);

			byte[] buffer = new byte[1024];
			MessageDigest msgdig = MessageDigest.getInstance("SHA-1");
			int numRead;

			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					msgdig.update(buffer, 0, numRead);
				}
			} while (numRead != -1);

			fis.close();
			byte[] digest = msgdig.digest();
			String str_result = "";
			for (int i = 0; i < digest.length; i++) {
				str_result += Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1);
			}
			return str_result;
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	public static byte[] fileToBytes(File file) {
		try {
			FileInputStream fis = new FileInputStream(file);
			byte[] buff = new byte[(int)file.length()];
			fis.read(buff);
			fis.close();
			return buff;
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return new byte[] {};
	}
}
