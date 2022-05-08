package legacyfix;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import legacyfix.protocol.http.Handler;

public class LegacyURLStreamHandlerFactory implements URLStreamHandlerFactory {

	public URLStreamHandler createURLStreamHandler(String protocol) {
		if ("http".equals(protocol)) {
			return new Handler();
		}

		return null;
	}
}
