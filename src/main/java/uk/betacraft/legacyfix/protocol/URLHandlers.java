package uk.betacraft.legacyfix.protocol;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.protocol.impl.*;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class URLHandlers {
    private static final List<Class<? extends HandlerBase>> handlers = Arrays.asList(
            SkinHandler.class,
            ResourceIndexHandler.class,
            JoinServerHandler.class,
            IndevAntiPiracyHandler.class,
            LevelListHandler.class,
            LevelSaveHandler.class,
            LevelLoadHandler.class,
            Minecraft1_6AvailableHandler.class,
            SnoopHandler.class,
            // https-only
            BetaAntiPiracyHandler.class,
            McoHandler.class
    );

    public static URLConnection matchHandler(URL url) {
        for (Class<? extends HandlerBase> handler : handlers) {
            try {
                Method regexPatternsMethod = handler.getMethod("regexPatterns");

                @SuppressWarnings("all")
                List<Pattern> patterns = (List<Pattern>) regexPatternsMethod.invoke(null);

                for (Pattern pattern : patterns) {
                    if (!url.toString().matches(pattern.pattern())) {
                        continue;
                    }

                    if (LevelHandlerBase.ONLINE_LEVEL_SERVER != null && handler.getSuperclass().equals(LevelHandlerBase.class)) {
                        String query = url.getQuery() != null ? "?" + url.getQuery() : "";

                        URLStreamHandler protocolHandler = LevelHandlerBase.ONLINE_LEVEL_SERVER.startsWith("http:") ?
                                new sun.net.www.protocol.http.Handler() : new sun.net.www.protocol.https.Handler();

                        String protocol = LevelHandlerBase.ONLINE_LEVEL_SERVER.startsWith("http") ?
                                "" : "https://";

                        return new URL(
                                null,
                                protocol + LevelHandlerBase.ONLINE_LEVEL_SERVER + "/proxy" + url.getPath() + query,
                                protocolHandler
                        ).openConnection();
                    }

                    return handler.getConstructor(URL.class, Pattern.class).newInstance(url, pattern);
                }
            } catch (Throwable t) {
                LFLogger.error("URLHandlers", t);
            }
        }

        return null;
    }
}
