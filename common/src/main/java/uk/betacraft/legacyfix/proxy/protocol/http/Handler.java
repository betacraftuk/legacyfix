package uk.betacraft.legacyfix.proxy.protocol.http;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.URLHandlers;
import uk.betacraft.legacyfix.util.web.RequestUtil;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * -Djava.protocol.handler.pkgs=uk.betacraft.legacyfix.protocol
 */
public class Handler extends URLStreamHandler {
    @Override
    protected URLConnection openConnection(URL url, Proxy p) throws IOException {
        return this.openConnection(url);
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        Logger.debug("Redirecting: " + url.toString());

        URLConnection lookup = URLHandlers.matchHandler(url);
        if (lookup != null) {
            return lookup;
        } else {
            return RequestUtil.createDirectURL(url.toString()).openConnection();
        }
    }
}
