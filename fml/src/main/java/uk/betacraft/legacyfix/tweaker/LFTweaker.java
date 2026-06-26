package uk.betacraft.legacyfix.tweaker;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.impl.thirdparty.FmlModContainerPatch;
import uk.betacraft.legacyfix.proxy.GameArgs;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

@SuppressWarnings({"unused", "rawtypes"})
public class LFTweaker implements ITweaker {
    public static Patcher patcher;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        String[] rawArgs = new String[args.size()];
        rawArgs = args.toArray(rawArgs);
        GameArgs.setArgsRaw(rawArgs);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        Tweakers.removeLwjglException(classLoader);
        patchLogger();

        patcher = new Patcher(Tweakers.createClassPool(classLoader.getURLs()));
        if (!Agent.loaded) {
            patcher.patches.add(new FmlModContainerPatch());
        }
        patcher.apply();

        classLoader.registerTransformer("uk.betacraft.legacyfix.tweaker.transformer.LFTransformer");
    }

    private void patchLogger() {
        try {
            Class logWrapperClass = Class.forName("net.minecraft.launchwrapper.LogWrapper");

            Field logInstanceField = logWrapperClass.getDeclaredField("log");
            logInstanceField.setAccessible(true);
            Object logInstance = logInstanceField.get(null);

            Field myLogField = logWrapperClass.getDeclaredField("myLog");
            myLogField.setAccessible(true);
            final Object logger = myLogField.get(logInstance);

            Object newLogger;
            if (logger instanceof java.util.logging.Logger) {
                newLogger = new uk.betacraft.legacyfix.tweaker.logger.LFLogger((java.util.logging.Logger) logger);
            } else {
                Class<?> loggerInterface = Class.forName("org.apache.logging.log4j.Logger");
                newLogger = Proxy.newProxyInstance(loggerInterface.getClassLoader(), new Class<?>[]{loggerInterface}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("log".equals(method.getName()) && args.length == 2) {
                            String message = String.valueOf(args[1]);
                            if (message != null && message.contains("but that path is defined and not secure")) {
                                return null;
                            }
                        }
                        return method.invoke(logger, args);
                    }
                });
            }
            myLogField.set(logInstance, newLogger);
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
