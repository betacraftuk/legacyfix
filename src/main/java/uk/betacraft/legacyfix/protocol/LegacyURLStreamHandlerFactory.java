package uk.betacraft.legacyfix.protocol;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class LegacyURLStreamHandlerFactory implements URLStreamHandlerFactory {

    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("http".equals(protocol)) {
            return new uk.betacraft.legacyfix.protocol.http.Handler();
        }

        return null;
    }
}
