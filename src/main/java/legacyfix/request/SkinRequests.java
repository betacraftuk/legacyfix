package legacyfix.request;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import legacyfix.util.Base64;
import legacyfix.util.ImageUtils;

public class SkinRequests {
	// name->uuid requests have a rate limit, so here's cache
	public static HashMap<String, String> nameToUUID = new HashMap<String, String>();

	// just in case if cache wasn't enough
	public static long haltReq	= -1L;
	public static Gson gson		= new Gson();

	public static boolean OVERLAY_OUTER_HEAD_LAYER		= System.getProperties().containsKey("lf.OVERLAY_OUTER_HEAD_LAYER");
	public static boolean OVERLAY_OUTER_BODY_TO_BASE	= System.getProperties().containsKey("lf.OVERLAY_OUTER_BODY_TO_BASE");
	public static boolean COPY_LEFT_ARM_TO_RIGHT		= System.getProperties().containsKey("lf.COPY_LEFT_ARM_TO_RIGHT");
	public static boolean COPY_LEFT_LEG_TO_RIGHT		= System.getProperties().containsKey("lf.COPY_LEFT_LEG_TO_RIGHT");
	public static boolean ROTATE_BOTTOM_TEXTURES		= System.getProperties().containsKey("lf.ROTATE_BOTTOM_TEXTURES");
	public static boolean CONVERT_ALEX_TO_STEVE		= System.getProperties().containsKey("lf.CONVERT_ALEX_TO_STEVE");
	public static boolean SERVE_AS_64x32			= System.getProperties().containsKey("lf.SERVE_AS_64x32");

	public static String getUUIDfromName(String username) {
		String cachedUUID = nameToUUID.get(username);

		if (cachedUUID != null) {
			return cachedUUID;
		} else {
			JsonObject obj = requestUUIDfromName(username);
			if (obj != null) {
				String uuid = obj.get("id").getAsString();

				nameToUUID.put(username, uuid);
				return uuid;
			}
		}

		return null;
	}

	public static JsonObject requestUUIDfromName(String name) {
		// wait for the block to pass
		if (haltReq > System.currentTimeMillis())
			return null;

		try {
			URL nametouuidURL	= new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
			JsonObject uuidjson	= gson.fromJson(new String(RequestUtil.readInputStream(nametouuidURL.openStream()), "UTF-8"), JsonObject.class);

			return uuidjson;
		} catch (Throwable t) {

			// halt requests for 5 mins to chill
			if (t.getMessage().contains("429 for URL"))
				haltReq = System.currentTimeMillis() + 300000;
			else
				t.printStackTrace();
		}
		return null;
	}

	public static SkinData fetchSkin(String uuid) {
		try {
			URL uuidtoprofileURL	= new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);

			JsonObject profilejson	= gson.fromJson(new String(RequestUtil.readInputStream(uuidtoprofileURL.openStream()), "UTF-8"), JsonObject.class);
			String base64tex		= profilejson.get("properties").getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString();
			String texjsonstr		= new String(Base64.decode(base64tex), "UTF-8");

			JsonObject texjson		= gson.fromJson(texjsonstr, JsonObject.class);
			JsonObject txts			= texjson.get("textures").getAsJsonObject();
			JsonObject skinjson		= txts.get("SKIN").getAsJsonObject();
			String skinURLstr		= skinjson.get("url").getAsString();

			byte[] officialCape = null;
			byte[] officialSkin = null;

			JsonElement capeelement = txts.get("CAPE");
			if (capeelement != null) {
				JsonObject capejson	= txts.get("CAPE").getAsJsonObject();
				String capeURLstr	= capejson.get("url").getAsString();
				URL capeURL			= new URL(capeURLstr);

				officialCape = RequestUtil.readInputStream(capeURL.openStream());
			}

			boolean isAlex = skinjson.get("metadata") != null; // watch if this gets more features in the future

			URL skinURL = new URL(skinURLstr);
			officialSkin = RequestUtil.readInputStream(skinURL.openStream());

			return new SkinData(officialSkin, officialCape, isAlex);
		} catch (Throwable t) {
			System.out.println("poop at fetchSkin: " + t.getMessage());
			t.printStackTrace();
		}
		return null;
	}

	// java is hella ugly
	public static class SkinData {
		public byte[] skin;
		public byte[] cape;
		public boolean alex;

		public SkinData(byte[] skin, byte[] cape, boolean alex) {
			this.skin = skin;
			this.cape = cape;
			this.alex = alex;
		}
	}

	public static byte[] getCape(String name) {
		String uuid = getUUIDfromName(name);
		SkinData skindata = fetchSkin(uuid);

		byte[] cape = null;
		try {
			if (skindata.cape != null) {
				ByteArrayInputStream bis = new ByteArrayInputStream(skindata.cape);

				ImageUtils imgu = new ImageUtils(bis);
				imgu = imgu.crop(0, 0, 64, 32);

				cape = imgu.getInByteForm();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return cape;
	}

	public static byte[] getSkin(String name) {
		String uuid = getUUIDfromName(name);
		SkinData skindata = fetchSkin(uuid);

		byte[] skin = null;
		try {
			if (skindata.skin != null) {
				ByteArrayInputStream bis = new ByteArrayInputStream(skindata.skin);
				ImageUtils imgu = new ImageUtils(bis);

				// classic before 0.24
				if (OVERLAY_OUTER_HEAD_LAYER)
					imgu = overlayHeadLayer(imgu);

				// before 1.8
				if (OVERLAY_OUTER_BODY_TO_BASE && imgu.getImage().getHeight() == 64)
					imgu = overlay64to32(imgu);

				// before 1.7.7
				if (COPY_LEFT_ARM_TO_RIGHT)
					useLeftArm(imgu);

				// before 1.7.7
				if (COPY_LEFT_LEG_TO_RIGHT)
					useLeftLeg(imgu);

				// before 1.8
				if (CONVERT_ALEX_TO_STEVE && skindata.alex)
					alexToSteve(imgu);

				// before b1.9-pre1
				if (ROTATE_BOTTOM_TEXTURES)
					rotateBottomTX(imgu);

				// before 1.8
				if (SERVE_AS_64x32)
					imgu = imgu.crop(0, 0, 64, 32);

				skin = imgu.getInByteForm();
			}
		} catch (Throwable t) {
			System.out.println("poop at getSkin: " + t.getMessage());
		}
		return skin;
	}

	public static void rotateBottomTX(ImageUtils imgu) {
		// bottom head
		imgu.setArea(16, 0, imgu.crop(16, 0, 8, 8).flip(false, true).getImage());
		// bottom head layer
		imgu.setArea(48, 0, imgu.crop(48, 0, 8, 8).flip(false, true).getImage());

		// bottom feet
		imgu.setArea(8, 16, imgu.crop(8, 16, 4, 4).flip(false, true).getImage());
		// bottom hand
		imgu.setArea(48, 16, imgu.crop(48, 16, 4, 4).flip(false, true).getImage());
		// bottom torso
		imgu.setArea(28, 16, imgu.crop(28, 16, 8, 4).flip(false, true).getImage());
	}

	public static ImageUtils overlay64to32(ImageUtils imgu) {
		// 32-64 body
		return imgu.setArea(0, 16, imgu.crop(0, 32, 56, 16).getImage(), false);
	}

	public static ImageUtils overlayHeadLayer(ImageUtils imgu) {
		return imgu.setArea(0, 0, imgu.crop(32, 0, 32, 16).getImage(), false);
	}

	public static void useLeftLeg(ImageUtils imgu) {
		// leg layer
		imgu.setArea(16, 48, imgu.crop(0, 48, 16, 16).getImage(), false);
		// overlay on the 0-32 part
		imgu.setArea(0, 16, imgu.crop(16, 48, 16, 16).getImage());
	}

	public static void useLeftArm(ImageUtils imgu) {
		// leg layer
		imgu.setArea(32, 48, imgu.crop(48, 48, 16, 16).getImage(), false);
		// overlay on the 0-32 part
		imgu.setArea(40, 16, imgu.crop(32, 48, 16, 16).getImage());
	}

	public static void alexToSteve(ImageUtils imgu) {
		// make space for the left arm 1px texture
		imgu.setArea(48, 20, imgu.crop(47, 20, 7, 12).getImage());
		// fill the 1px space in between
		imgu.setArea(47, 20, imgu.crop(46, 20, 1, 12).getImage());
		// fill the 1px space on the right
		imgu.setArea(55, 20, imgu.crop(54, 20, 1, 12).getImage());

		// bottom hand
		imgu.setArea(48, 16, imgu.crop(47, 16, 3, 4).getImage());
		// fill the 1px of the shoulder
		imgu.setArea(47, 16, imgu.crop(46, 16, 1, 4).getImage());
		// fill the 1px space on the right
		imgu.setArea(51, 16, imgu.crop(50, 16, 1, 4).getImage());
	}
}
