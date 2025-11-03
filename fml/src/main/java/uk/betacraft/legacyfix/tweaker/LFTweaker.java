package uk.betacraft.legacyfix.tweaker;

import javassist.ClassPool;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.launchwrapper.LogWrapper;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.impl.fml.ForgeModInjectPatch;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"unused", "unchecked"})
public class LFTweaker implements ITweaker {
    protected static Patcher patcher;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        try {
            Field exceptionsField = LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
            exceptionsField.setAccessible(true);

            Set<String> exceptions = (Set<String>) exceptionsField.get(classLoader);
            exceptions.remove("org.lwjgl.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        patchLogger();

        ClassPool pool = new ClassPool(true);
        try {
            for (URL url : classLoader.getURLs()) {
                pool.appendClassPath(url.toURI().getPath());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        patcher = new Patcher(pool);
        patcher.patches.add(new ForgeModInjectPatch());
        patcher.apply();
        classLoader.registerTransformer("uk.betacraft.legacyfix.tweaker.LFTransformer");
    }

    private void patchLogger() {
        try {
            Field loggerField = LogWrapper.class.getDeclaredField("myLog");
            loggerField.setAccessible(true);

            Object logger = loggerField.get(LogWrapper.log);
            Object newLogger;
            if (logger instanceof java.util.logging.Logger) {
                newLogger = new uk.betacraft.legacyfix.tweaker.logger.LFLogger((java.util.logging.Logger) logger);
            } else {
                newLogger = new uk.betacraft.legacyfix.tweaker.logger.LFLog4jLogger((org.apache.logging.log4j.core.Logger) logger);
            }
            loggerField.set(LogWrapper.log, newLogger);
        } catch (Exception e) {
            System.out.println("Couldn't silence seal class errors!");
            e.printStackTrace();
        }
    }

    @Override
    public String getLaunchTarget() {
        return "";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
