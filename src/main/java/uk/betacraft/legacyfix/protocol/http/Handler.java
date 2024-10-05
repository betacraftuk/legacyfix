package uk.betacraft.legacyfix.protocol.http;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.protocol.impl.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * -Djava.protocol.handler.pkgs=uk.betacraft.legacyfix.protocol
 */
public class Handler extends URLStreamHandler {
    private static final List<Class<? extends HandlerBase>> handlers = Arrays.asList(
            SkinHandler.class,
            ResourceIndexHandler.class,
            JoinServerHandler.class,
            IndevAntiPiracyHandler.class,
            BetaAntiPiracyHandler.class,
            LevelListHandler.class,
            LevelSaveHandler.class,
            LevelLoadHandler.class,
            Minecraft1_6AvailableHandler.class
    );

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        if (LegacyFixAgent.isDebug())
            LFLogger.info("Redirecting: " + url.toString());

        for (Class<? extends HandlerBase> handler : handlers) {
            try {
                Method regexPatternsMethod = handler.getMethod("regexPatterns");

                @SuppressWarnings("all")
                List<Pattern> patterns = (List<Pattern>) regexPatternsMethod.invoke(null);

                for (Pattern pattern : patterns) {
                    if (url.toString().matches(pattern.pattern())) {
                        return handler.getConstructor(URL.class, Pattern.class).newInstance(url, pattern);
                    }
                }
            } catch (Throwable t) {
                LFLogger.error("HTTP-Handler", t);
            }
        }

        return new URL(null, url.toString(), new sun.net.www.protocol.http.Handler()).openConnection();
    }
}
