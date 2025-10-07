package uk.betacraft.legacyfix.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import uk.betacraft.legacyfix.LFLogger;

import java.security.Security;

public class BouncyCastleUtils {
    private static boolean inited = false;

    public static void init() {
        if (inited) {
            return;
        }

        LFLogger.debug("Initializing Bouncy Castle...");

        System.setProperty("org.bouncycastle.jsse.client.assumeOriginalHostName", "true");
        Security.setProperty("ssl.SocketFactory.provider", "org.bouncycastle.jsse.provider.SSLSocketFactoryImpl");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);

        LFLogger.debug("Initialized Bouncy Castle.");

        inited = true;
    }
}
