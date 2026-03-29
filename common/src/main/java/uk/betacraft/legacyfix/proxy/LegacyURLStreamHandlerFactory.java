package uk.betacraft.legacyfix.proxy;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class LegacyURLStreamHandlerFactory implements URLStreamHandlerFactory {
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("http".equals(protocol)) {
            return new uk.betacraft.legacyfix.proxy.protocol.http.Handler();
        } else if ("https".equals(protocol)) {
            return new uk.betacraft.legacyfix.proxy.protocol.https.Handler();
        }

        return null;
    }
}
