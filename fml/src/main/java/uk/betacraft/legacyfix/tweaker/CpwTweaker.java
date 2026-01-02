package uk.betacraft.legacyfix.tweaker;

import cpw.mods.fml.relauncher.IFMLCallHook;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.tweaker.logger.LFLogger;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class CpwTweaker implements IFMLCallHook {
    public static Patcher patcher;
    private URLClassLoader classLoader;

    @Override
    public void injectData(Map<String, Object> data) {
        classLoader = (URLClassLoader) data.get("classLoader");
    }

    @Override
    public Void call() throws Exception {
        Tweakers.removeLwjglException(classLoader);
        patchLogger();

        patcher = new Patcher(Tweakers.createClassPool(classLoader.getURLs()));
        patcher.apply();

        classLoader.getClass()
            .getDeclaredMethod("registerTransformer", String.class)
            .invoke(classLoader, "uk.betacraft.legacyfix.tweaker.transformer.LFCpwTransformer");

        return null;
    }

    private void patchLogger() {
        try {
            Class fmlLogClass = Class.forName("cpw.mods.fml.common.FMLLog");
            Field loggerField = fmlLogClass.getDeclaredField("coreLog");
            loggerField.setAccessible(true);

            Object fmlRelaunchLog = loggerField.get(null);
            Class fmlRelaunchLogClass = fmlRelaunchLog.getClass();

            Field myLogField = fmlRelaunchLogClass.getDeclaredField("myLog");
            myLogField.setAccessible(true);
            Object oldLogger = myLogField.get(fmlRelaunchLog);
            myLogField.set(fmlRelaunchLog, new LFLogger((java.util.logging.Logger) oldLogger));
        } catch (Exception e) {
            System.out.println("Couldn't silence seal class errors!");
            e.printStackTrace();
        }
    }
}
