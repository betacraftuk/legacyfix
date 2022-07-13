package legacyfix;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import legacyfix.request.SkinRequests;

public class SkinURLConnection extends HttpURLConnection {
	public static String[] capeURLs = {
			"http://www.minecraft.net/cloak/get.jsp?user=",
			"http://s3.amazonaws.com/MinecraftCloaks/",
			"http://skins.minecraft.net/MinecraftCloaks/"
	};

	public static String[] skinURLs = {
			"http://www.minecraft.net/skin/",
			"http://s3.amazonaws.com/MinecraftSkins/",
			"http://skins.minecraft.net/MinecraftSkins/"
	};

	public SkinURLConnection(URL url) {
		super(url);
	}

	@Override
	public void disconnect() {}

	@Override
	public boolean usingProxy() {
		return false;
	}

	private InputStream stream;

	@Override
	public void connect() throws IOException {
		String username = null;
		boolean cape = false;

		for (String template : skinURLs) {
			if (url.toString().startsWith(template))
				username = url.toString().substring(template.length());
		}

		for (String template : capeURLs) {
			if (url.toString().startsWith(template)) {
				username = url.toString().substring(template.length());
				cape = true;
			}
		}

		username = username.replace(".png", "");

		if (cape) {
			stream = new ByteArrayInputStream(SkinRequests.getCape(username));
		} else {
			stream = new ByteArrayInputStream(SkinRequests.getSkin(username));
		}
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return this.stream;
	}

	@Override
	public int getResponseCode() {
		return 200;
	}
}