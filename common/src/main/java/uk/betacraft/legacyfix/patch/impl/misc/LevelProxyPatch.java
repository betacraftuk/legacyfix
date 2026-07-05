package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.proxy.LevelProxyAuthenticator;
import uk.betacraft.legacyfix.proxy.handlers.LevelHandlerBase;

import java.applet.Applet;
import java.lang.reflect.Field;

public class LevelProxyPatch extends Patch {
    private static Applet applet;
    private static String minecraftField;
    private static String sessionField;
    private static String tokenField;
    private static boolean authStarted;

    public LevelProxyPatch() {
        super("level-proxy", "", true, false);
    }

    @SuppressWarnings("unused")
    public static synchronized void register(Applet applet) {
        LevelProxyPatch.applet = applet;
        if (!authStarted && LevelHandlerBase.ONLINE_LEVEL_SERVER != null) {
            authStarted = true;
            new LevelProxyAuthenticator().start();
        }
        update();
    }

    public static synchronized void update() {
        if (LevelHandlerBase.levelToken == null || applet == null || minecraftField == null || sessionField == null || tokenField == null) {
            return;
        }

        try {
            Object minecraft = getField(applet.getClass(), minecraftField).get(applet);
            if (minecraft == null) {
                return;
            }

            Object session = getField(minecraft.getClass(), sessionField).get(minecraft);
            if (session != null) {
                getField(session.getClass(), tokenField).set(session, LevelHandlerBase.levelToken);
            }
        } catch (Exception e) {
            Logger.error("LevelProxyPatch", e);
        }
    }

    private static Field getField(Class cls, String name) throws NoSuchFieldException {
        Field field = cls.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public void apply(PatchPool patchPool) throws Exception {
        final String appletClass = GameClasses.findMinecraftAppletClass(patchPool);
        minecraftField = GameClasses.findMinecraftFieldName(patchPool);
        sessionField = GameClasses.findSessionFieldName(patchPool);
        tokenField = findTokenField(patchPool);

        if (appletClass == null || minecraftField == null || sessionField == null || tokenField == null) {
            Logger.debug("LevelProxyPatch", "Couldn't resolve applet session fields");
            return;
        }

        patchPool.addCtTransformer(appletClass, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod init = ctClass.getDeclaredMethod("init");
                init.insertAfter("" +
                    "Class patchClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.misc.LevelProxyPatch\");" +
                    "patchClass.getMethod(\"register\", new Class[]{java.applet.Applet.class}).invoke(null, new Object[]{(java.applet.Applet) $0});"
                );
            }
        });
    }

    private String findTokenField(PatchPool patchPool) throws Exception {
        String sessionClassName = GameClasses.findSessionClassName(patchPool);
        if (sessionClassName == null) {
            return null;
        }

        CtClass sessionClass = patchPool.getRawClass(sessionClassName);
        int stringFields = 0;
        for (CtField field : sessionClass.getDeclaredFields()) {
            if ("java.lang.String".equals(field.getType().getName()) && ++stringFields == 2) {
                Logger.debug("Found session token field: " + field.getName());
                return field.getName();
            }
        }

        return null;
    }
}
