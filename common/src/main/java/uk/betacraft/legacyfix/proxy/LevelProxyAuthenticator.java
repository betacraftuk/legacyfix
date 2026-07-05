package uk.betacraft.legacyfix.proxy;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.impl.misc.LevelProxyPatch;
import uk.betacraft.legacyfix.proxy.handlers.LevelHandlerBase;
import uk.betacraft.legacyfix.util.HashUtils;
import uk.betacraft.legacyfix.util.web.Request;
import uk.betacraft.legacyfix.util.web.RequestUtil;
import uk.betacraft.legacyfix.util.web.WebData;

public class LevelProxyAuthenticator extends Thread {
    @Override
    public void run() {
        String levelServer = LevelProxyConfig.getOnlineLevelServer();
        if (levelServer == null) {
            return;
        }

        String uuid = GameArgs.getUuid();

        WebData joinServerResponse = RequestUtil.performJoinServer(
            uuid,
            GameArgs.getSession(),
            HashUtils.sha1(RequestUtil.getIPFromAmazon())
        );

        if (!joinServerResponse.successful()) {
            Logger.error("Failed to authenticate with Mojang for online level saving");
            Logger.error("LevelProxyAuthenticator",
                "" + joinServerResponse.getResponseCode(),
                joinServerResponse.toString()
            );
            return;
        }

        String protocol = levelServer.startsWith("http") ?
            "" : "https://";

        Request sessionRequest = new Request();
        sessionRequest.setUrl(protocol + levelServer + "/api/proxy_token?player=" + uuid);

        WebData proxyAuthResponse = RequestUtil.performRawGETRequest(sessionRequest);
        if (!proxyAuthResponse.successful()) {
            Logger.error("Failed to authenticate with the proxy server for online level saving");
            Logger.error("LevelProxyAuthenticator",
                "" + proxyAuthResponse.getResponseCode(),
                proxyAuthResponse.toString()
            );
            return;
        }

        LevelHandlerBase.levelToken = proxyAuthResponse.toString();
        LevelProxyPatch.update();
        Logger.info("Authenticated with level proxy server: " + levelServer);
    }
}
