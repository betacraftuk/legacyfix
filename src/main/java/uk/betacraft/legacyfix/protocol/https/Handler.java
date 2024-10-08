package uk.betacraft.legacyfix.protocol.https;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.protocol.URLHandlers;

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
        if (LegacyFixAgent.isDebug())
            LFLogger.info("Redirecting: " + url.toString());

        URLConnection lookup = URLHandlers.matchHandler(url);
        if (lookup != null)
            return lookup;
        else
            return new URL(null, url.toString(), new sun.net.www.protocol.https.Handler()).openConnection();
    }
}
