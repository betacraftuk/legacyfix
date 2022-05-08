package legacyfix.protocol.https;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

// This fixes skins in certain versions (like 1.7.7)
// TODO: find out a way to import ssl certificates from newer versions of Java
public class Handler extends URLStreamHandler {
	public static final boolean RedirectProfile = "true".equalsIgnoreCase(System.getProperty("legacyfix.redirectProfile"));

	@Override
	protected URLConnection openConnection(URL url, Proxy p) throws IOException {
		return this.openConnection(url);
	}

	@Override
	protected URLConnection openConnection(URL url) throws IOException {
		String stringurl = url.toString();

		if (RedirectProfile && stringurl.contains("/session/minecraft/profile/") && !stringurl.contains("?unsigned=") && !stringurl.endsWith("?unsigned=false"))
			stringurl += "?unsigned=false";
		
		return new URL((URL)null, stringurl, new sun.net.www.protocol.https.Handler()).openConnection();
	}
}