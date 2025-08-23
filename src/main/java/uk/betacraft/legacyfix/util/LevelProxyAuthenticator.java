package uk.betacraft.legacyfix.util;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixLauncher;
import uk.betacraft.legacyfix.protocol.impl.LevelHandlerBase;
import uk.betacraft.util.Request;
import uk.betacraft.util.RequestUtil;
import uk.betacraft.util.WebData;

public class LevelProxyAuthenticator extends Thread {

    @Override
    public void run() {
        String uuid = LegacyFixLauncher.getUUID();

        WebData joinServerResponse = RequestUtil.performJoinServer(
            uuid,
            LegacyFixLauncher.getSessionId(),
            HashUtils.sha1(RequestUtil.getIPFromAmazon())
        );

        if (!joinServerResponse.successful()) {
            LFLogger.error("Failed to authenticate with Mojang for online level saving");
            LFLogger.error("LevelProxyAuthenticator",
                "" + joinServerResponse.getResponseCode(),
                joinServerResponse.toString()
            );
            return;
        }

        String protocol = LevelHandlerBase.ONLINE_LEVEL_SERVER.startsWith("http") ?
            "" : "https://";

        Request sessionRequest = new Request();
        sessionRequest.setUrl(protocol + LevelHandlerBase.ONLINE_LEVEL_SERVER + "/api/proxy_token?player=" + uuid);

        WebData proxyAuthResponse = RequestUtil.performRawGETRequest(sessionRequest);
        if (!proxyAuthResponse.successful()) {
            LFLogger.error("Failed to authenticate with the proxy server for online level saving");
            LFLogger.error("LevelProxyAuthenticator",
                "" + proxyAuthResponse.getResponseCode(),
                proxyAuthResponse.toString()
            );
            return;
        }

        LegacyFixLauncher.setValue("sessionid", proxyAuthResponse.toString());
        LFLogger.info("Authenticated with level proxy server: " + LevelHandlerBase.ONLINE_LEVEL_SERVER);
    }
}
