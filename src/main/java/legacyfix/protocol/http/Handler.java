package legacyfix.protocol.http;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import legacyfix.BasicResponseURLConnection;
import legacyfix.JoinServerURLConnection;
import legacyfix.ListLevelsURLConnection;
import legacyfix.LoadLevelURLConnection;
import legacyfix.ResourceIndexURLConnection;
import legacyfix.SaveLevelURLConnection;
import legacyfix.SkinURLConnection;

// thanks codie <3 - https://github.com/craftycodie/MineOnline
public class Handler extends URLStreamHandler {

	@Override
	protected URLConnection openConnection(URL url, Proxy p) throws IOException {
		return this.openConnection(url);
	}

	@Override
	protected URLConnection openConnection(URL url) throws IOException {
		// TODO resources handling (pre-a1.1.2_01)

		System.out.println("Got " + url.toString());
		if (url.toString().endsWith("/game/joinserver.jsp"))
			return new JoinServerURLConnection(url);
		else if (url.toString().contains("/login/session.jsp")) // May be unused.
			return new BasicResponseURLConnection(url, 200, "ok");
		else if (url.toString().contains("login.minecraft.net/session?name="))
			return new BasicResponseURLConnection(url, 200, "ok");
		else if (url.toString().contains("/game/"))
			return new BasicResponseURLConnection(url, 200, "42069");
		else if (url.toString().endsWith("1_6_has_been_released.flag"))
			return new BasicResponseURLConnection(url, 403, "");
		else if (url.toString().contains("/haspaid.jsp"))
			return new BasicResponseURLConnection(url, 200, "true");
		// These move classic worlds to local files, as the level api is long gone.
		else if (url.toString().contains("level/save.html"))
			return new SaveLevelURLConnection(url);
		else if (url.toString().contains("level/load.html"))
			return new LoadLevelURLConnection(url);
		else if (url.toString().contains("listmaps.jsp"))
			return new ListLevelsURLConnection(url);
		// Sounds are downloaded by the launcher, so if this returns 404 the client is going to load them without checking
		else if (url.toString().endsWith("/MinecraftResources/"))
			return new ResourceIndexURLConnection(url, true);
		else if (url.toString().endsWith("/resources/"))
			return new ResourceIndexURLConnection(url, false);
		// Skins are pulled from the new endpoint and converted to the legacy format as required. Genius.
		else if (url.toString().contains("/MinecraftSkins/") || url.toString().contains("/skin/"))
			return new SkinURLConnection(url);
		// Capes are pulled from the new endpoint.
		else if ((url.toString().contains("/MinecraftCloaks/") && url.toString().contains(".png")) || url.toString().contains("/cloak/get.jsp?user="))
			return new SkinURLConnection(url);
		else {
			System.out.println("Redirecting " + url.toString());
			return new URL((URL)null, url.toString(), new sun.net.www.protocol.http.Handler()).openConnection();
		}
	}
}