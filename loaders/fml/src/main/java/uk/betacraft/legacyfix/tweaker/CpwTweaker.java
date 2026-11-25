package uk.betacraft.legacyfix.tweaker;

import cpw.mods.fml.relauncher.IFMLCallHook;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.tweaker.logger.LFLogger;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.List;
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
        moveTransformerBeforeDeobf();

        return null;
    }

    @SuppressWarnings("unchecked")
    private void moveTransformerBeforeDeobf() {
        try {
            Field transformersField = classLoader.getClass().getDeclaredField("transformers");
            transformersField.setAccessible(true);
            List<Object> transformers = (List<Object>) transformersField.get(classLoader);

            int deobfIndex = -1;
            int ourIndex = -1;
            for (int i = 0; i < transformers.size(); i++) {
                String name = transformers.get(i).getClass().getName();
                if (deobfIndex == -1 && "cpw.mods.fml.common.asm.transformers.DeobfuscationTransformer".equals(name)) {
                    deobfIndex = i;
                } else if ("uk.betacraft.legacyfix.tweaker.transformer.LFCpwTransformer".equals(name)) {
                    ourIndex = i;
                }
            }

            if (deobfIndex != -1 && ourIndex > deobfIndex) {
                transformers.add(deobfIndex, transformers.remove(ourIndex));
            }
        } catch (Exception e) {
            Logger.error("Couldn't move LF before FML's deobf transformer!");
            Logger.error("CpwTweaker.moveTransformerBeforeDeobf", e);
        }
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
            Logger.error("Couldn't silence seal class errors!");
            Logger.error("CpwTweaker.patchLogger", e);
        }
    }
}
