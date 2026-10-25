package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import org.json.JSONObject;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.patch.GameClasses;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.StreamUtils;

import java.io.InputStream;

public class ProxyPatch extends Patch {
    public ProxyPatch() {
        super("proxy", "", true, false);
    }

    public void apply(PatchPool patchPool) throws Exception {
        String version = Agent.getSetting("lf.version");
        if (version == null) {
            readGameVersion(patchPool);
        } else {
            GameArgs.setVersion(version);
        }

        // Set by tweakers before patches are applied
        if (GameArgs.initialized()) {
            return;
        }

        String cmd = System.getProperty("sun.java.command");
        if (cmd != null) {
            if (cmd.contains("org.prismlauncher.EntryPoint") &&
                findArgsByMMCLauncher(
                    patchPool,
                    "org.prismlauncher.launcher.impl.AbstractLauncher",
                    "org.prismlauncher.utils.Parameters",
                    "getList",
                    "getString"
                )) {
                return;
            }

            if (cmd.contains("org.multimc.EntryPoint") &&
                findArgsByMMCLauncher(
                    patchPool,
                    "org.multimc.onesix.OneSixLauncher",
                    "org.multimc.ParamBucket",
                    "allSafe",
                    "firstSafe"
                )) {
                return;
            }

            if (cmd.contains("uk.betacraft.legacyfix.applet.AppletLauncher")) {
                return;
            }
        }

        String prismArgs = System.getProperty("org.prismlauncher.launch.gameargs");
        if (!Agent.active && prismArgs != null) {
            String[] args = prismArgs.split("\u001F");
            GameArgs.setArgsRaw(args);
            return;
        }

        if (findArgsWithApplet(patchPool)) {
            return;
        }

        if (findArgsByMain(patchPool)) {
            return;
        }

        if (findArgsWithWrapper(patchPool)) {
            return;
        }

        Logger.error(
            "",
            "Game arguments could not be found and the proxy might not work properly.",
            "If you experience issues, report this to the LegacyFix GitHub along with info about your game setup.",
            ""
        );
    }

    private void readGameVersion(PatchPool patchPool) throws Exception {
        String minecraftClass = GameClasses.findMinecraftClass(patchPool);
        if (minecraftClass == null) {
            Logger.error("Couldn't find the main game class! Please specify your game version with the -Dlf.version argument.");
            return;
        }

        if (minecraftClass.equals("com.mojang.rubydung.RubyDung") || minecraftClass.equals("com.mojang.minecraft.RubyDung")) {
            // pre-Classic does not call Display.setTitle
            GameArgs.setVersion("Minecraft pc-(date)");
            return;
        }

        CtClass ctClass = patchPool.getRawClass(minecraftClass);
        readGameVersionPre1_13(ctClass);

        if (Agent.getSetting("lf.version") == null) {
            readGameVersionFromVersionJson();
        }

        if (Agent.getSetting("lf.version") == null) {
            readGameVersion1_13(ctClass, patchPool);
        }

        if (Agent.getSetting("lf.version") == null) {
            Logger.error("Couldn't read the game version! Please specify your game version with the -Dlf.version argument.");
        }
    }

    // reads game version from calling Display.setTitle. useful for c0.0.2a - 1.12.2
    private void readGameVersionPre1_13(CtClass minecraftClass) {
        for (CtMethod method : minecraftClass.getDeclaredMethods()) {
            try {
                CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
                CodeIterator codeIterator = codeAttribute.iterator();
                ConstPool cp = method.getMethodInfo().getConstPool();

                while (codeIterator.hasNext()) {
                    int pos = codeIterator.next();
                    int opcode = codeIterator.byteAt(pos);
                    if (opcode != Opcode.LDC && opcode != Opcode.LDC_W) {
                        continue;
                    }

                    int ldcIndex;
                    if (opcode == Opcode.LDC_W) {
                        ldcIndex = codeIterator.u16bitAt(pos + 1);
                    } else {
                        ldcIndex = codeIterator.byteAt(pos + 1);
                    }
                    if (cp.getTag(ldcIndex) != ConstPool.CONST_String) {
                        continue;
                    }

                    String minecraftTitle = cp.getStringInfo(ldcIndex);

                    int posInvoke = pos + 2 + (opcode == Opcode.LDC_W ? 1 : 0);
                    if (!codeIterator.hasNext() || codeIterator.byteAt(posInvoke) != Opcode.INVOKESTATIC) {
                        continue;
                    }

                    int methodIndex = codeIterator.u16bitAt(posInvoke + 1);
                    if (!"org.lwjgl.opengl.Display".equals(cp.getMethodrefClassName(methodIndex))) {
                        continue;
                    }

                    if ("setTitle".equals(cp.getMethodrefName(methodIndex))) {
                        GameArgs.setVersion(minecraftTitle);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    // reads game version from calling GLFW.glfwCreateWindow. useful for 17w43a (1.13) - 18w47a (1.14)
    private void readGameVersion1_13(CtClass minecraftClass, PatchPool patchPool) {
        String windowClassName = null;
        for (CtMethod method : minecraftClass.getDeclaredMethods()) {
            boolean isInitMethod = false;
            try {
                CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
                CodeIterator codeIterator = codeAttribute.iterator();
                ConstPool cp = method.getMethodInfo().getConstPool();

                while (codeIterator.hasNext()) {
                    int pos = codeIterator.next();
                    int opcode = codeIterator.byteAt(pos);
                    if (opcode != Opcode.LDC && opcode != Opcode.LDC_W) {
                        continue;
                    }

                    int ldcIndex;
                    if (opcode == Opcode.LDC_W) {
                        ldcIndex = codeIterator.u16bitAt(pos + 1);
                    } else {
                        ldcIndex = codeIterator.byteAt(pos + 1);
                    }
                    if (cp.getTag(ldcIndex) != ConstPool.CONST_String) {
                        continue;
                    }

                    if (!codeIterator.hasNext() || codeIterator.byteAt(codeIterator.next()) != Opcode.INVOKESTATIC) {
                        continue;
                    }

                    if (!codeIterator.hasNext() || codeIterator.byteAt(pos = codeIterator.next()) != Opcode.INVOKEINTERFACE) {
                        continue;
                    }

                    int methodIndex = codeIterator.u16bitAt(pos + 1);
                    if (!"org.apache.logging.log4j.Logger".equals(cp.getMethodrefClassName(methodIndex))) {
                        continue;
                    }

                    if (!"info".equals(cp.getMethodrefName(methodIndex))) {
                        continue;
                    }

                    if (!"LWJGL Version: {}".equals(cp.getStringInfo(ldcIndex))) {
                        continue;
                    }

                    isInitMethod = true;
                    break;
                }

                if (!isInitMethod) {
                    continue;
                }

                Logger.debug("Found Minecraft.init() method: " + method.getName());

                while (codeIterator.hasNext()) {
                    int pos = codeIterator.next();
                    int opcode = codeIterator.byteAt(pos);
                    if (opcode == Opcode.LDC || opcode == Opcode.LDC_W) {
                        int ldcIndex;
                        if (opcode == Opcode.LDC_W) {
                            ldcIndex = codeIterator.u16bitAt(pos + 1);
                        } else {
                            ldcIndex = codeIterator.byteAt(pos + 1);
                        }
                        if (cp.getTag(ldcIndex) != ConstPool.CONST_String) {
                            continue;
                        }

                        String ldcString = cp.getStringInfo(ldcIndex);
                        if (ldcString != null && ldcString.startsWith("Minecraft ")) {
                            // 18w44a - 18w47a
                            GameArgs.setVersion(ldcString);
                            return;
                        }
                    }

                    // 17w43a - 18w43a
                    if (codeIterator.byteAt(pos) != Opcode.INVOKEVIRTUAL) {
                        continue;
                    }

                    int methodIndex = codeIterator.u16bitAt(pos + 1);
                    String signature = cp.getMethodrefType(methodIndex);
                    if (signature == null) {
                        continue;
                    }

                    String paramTypes = signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')'));

                    if (!paramTypes.contains(";")) {
                        continue;
                    }

                    String[] split = paramTypes.split(";");

                    if (split.length != 2) {
                        continue;
                    }

                    if (!"Ljava/lang/String".equals(split[1])) {
                        continue;
                    }

                    String returnType = signature.substring(signature.lastIndexOf(')') + 1);

                    if (!returnType.startsWith("L") || !returnType.endsWith(";")) {
                        continue;
                    }

                    windowClassName = returnType.substring(1, returnType.lastIndexOf(";"));
                    break;
                }

                if (windowClassName != null) {
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        if (windowClassName == null) {
            return;
        }

        CtClass windowClass = patchPool.getRawClass(windowClassName);
        for (CtConstructor constructor : windowClass.getDeclaredConstructors()) {
            try {
                CodeAttribute codeAttribute = constructor.getMethodInfo().getCodeAttribute();
                CodeIterator codeIterator = codeAttribute.iterator();
                ConstPool cp = constructor.getMethodInfo().getConstPool();

                while (codeIterator.hasNext()) {
                    int pos = codeIterator.next();
                    int opcode = codeIterator.byteAt(pos);
                    if (opcode != Opcode.LDC && opcode != Opcode.LDC_W) {
                        continue;
                    }

                    int ldcIndex;
                    if (opcode == Opcode.LDC_W) {
                        ldcIndex = codeIterator.u16bitAt(pos + 1);
                    } else {
                        ldcIndex = codeIterator.byteAt(pos + 1);
                    }
                    if (cp.getTag(ldcIndex) != ConstPool.CONST_String) {
                        continue;
                    }

                    String ldcString = cp.getStringInfo(ldcIndex);
                    if (ldcString != null && ldcString.startsWith("Minecraft ")) {
                        GameArgs.setVersion(ldcString);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    // reads game version from 'version.json' featured in Minecraft client jars of 18w47b (1.14) and later
    private void readGameVersionFromVersionJson() {
        String versionJson;
        try {
            InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream("version.json");
            if (stream == null) {
                return;
            }

            byte[] bytes = StreamUtils.readInputStream(stream);
            if (bytes == null) {
                return;
            }

            versionJson = new String(bytes, "UTF-8");
        } catch (Throwable t) {
            Logger.error(this, t);
            return;
        }

        JSONObject version = new JSONObject(versionJson);
        String verId = version.optString("id", null);
        if (verId == null) {
            return;
        }

        if (verId.contains(" / ")) {
            verId = verId.split(" / ")[0];
        }

        GameArgs.setVersion(verId);
    }

    private boolean findArgsWithApplet(PatchPool patchPool) throws Exception {
        String minecraftClass = GameClasses.findMinecraftClass(patchPool);
        String appletClass = GameClasses.findMinecraftAppletClass(patchPool);

        if (minecraftClass == null || appletClass == null) {
            return false;
        }

        final String[] appletFieldName = { null };
        {
            CtClass mc = patchPool.getRawClass(minecraftClass);

            for (CtField field : mc.getDeclaredFields()) {
                if (appletClass.equals(field.getType().getName())) {
                    appletFieldName[0] = field.getName();
                    Logger.debug("Found applet field: " + appletFieldName[0]);
                    break;
                }
            }
        }

        if (appletFieldName[0] == null) {
            return false;
        }

        patchPool.addCtTransformer(minecraftClass, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod mainMethod = null;
                for (CtMethod method : ctClass.getDeclaredMethods()) {
                    if (method.getName().equals("main")) {
                        mainMethod = method;
                    }
                }
                if (mainMethod == null) {
                    return;
                }

                mainMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "Class assetUtilsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.assets.AssetUtils\");" +
                    "boolean initialized = ((java.lang.Boolean) gameArgsClass.getMethod(\"initialized\", null).invoke(null, null)).booleanValue();" +
                    "if (!initialized) {" +
                    "   gameArgsClass.getMethod(\"setArgsRaw\", new Class[]{String[].class}).invoke(null, new Object[]{$1});" +
                    "   assetUtilsClass.getMethod(\"downloadAssets\", null).invoke(null, null);" +
                    "}"
                );

                CtMethod runMethod = ctClass.getDeclaredMethod("run");
                runMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "boolean initialized = ((java.lang.Boolean) gameArgsClass.getMethod(\"initialized\", null).invoke(null, null)).booleanValue();" +
                    "java.applet.Applet applet = (java.applet.Applet) $0." + appletFieldName[0] + ";" +
                    "if (!initialized && (applet != null)) {" +
                    "   gameArgsClass.getMethod(\"setArgs\", new Class[]{String.class, String.class}).invoke(null, new Object[]{" +
                    "       applet.getParameter(\"username\")," +
                    "       applet.getParameter(\"sessionid\")" +
                    "   });" +
                    "}" +
                    "if (!initialized) {" +
                    "   Class assetUtilsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.assets.AssetUtils\");" +
                    "   assetUtilsClass.getMethod(\"downloadAssets\", null).invoke(null, null);" +
                    "}"
                );
            }
        });

        return true;
    }

    private boolean findArgsByMain(PatchPool patchPool) throws Exception {
        if (!Agent.active) {
            return false;
        }

        CtClass mainClass = patchPool.getRawClass("net.minecraft.client.main.Main");
        if (mainClass == null) {
            return false;
        }

        patchPool.addCtTransformer("net.minecraft.client.main.Main", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod mainMethod = ctClass.getDeclaredMethod("main");
                mainMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "gameArgsClass.getMethod(\"setArgsRaw\", new Class[]{String[].class}).invoke(null, new Object[]{$1});" +
                    "Class assetUtilsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.assets.AssetUtils\");" +
                    "assetUtilsClass.getMethod(\"downloadAssets\", null).invoke(null, null);"
                );
            }
        });

        return true;
    }

    private boolean findArgsByMMCLauncher(final PatchPool patchPool, String mmcLauncherName, final String mmcParamsName, final String mmcParamsGetListName, final String mmcParamsGetStringName) throws Exception {
        if (!Agent.active) {
            return false;
        }

        CtClass launcherClass = patchPool.getRawClass(mmcLauncherName);
        if (launcherClass == null) {
            return false;
        }

        patchPool.addCtTransformer(mmcLauncherName, new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtClass mmcParamsClass = patchPool.getRawClass(mmcParamsName);
                if (mmcParamsClass == null) {
                    return;
                }

                CtConstructor mainMethod = ctClass.getDeclaredConstructor(new CtClass[]{mmcParamsClass});
                mainMethod.insertBefore("" +
                    "String mainClass = $1." + mmcParamsGetStringName + "(\"mainClass\", \"\");" +
                    "if (!mainClass.equals(\"uk.betacraft.legacyfix.applet.AppletLauncher\")) {" +
                    "    java.util.List argsList = $1." + mmcParamsGetListName + "(\"param\", new java.util.ArrayList());" +
                    "    String serverAddress = $1." + mmcParamsGetStringName + "(\"serverAddress\", null);" +
                    "    String serverPort = $1." + mmcParamsGetStringName + "(\"serverPort\", null);" +
                    "    if (serverAddress != null) {" +
                    "        argsList.add(\"--server\");" +
                    "        argsList.add(serverAddress);" +
                    "        argsList.add(\"--port\");" +
                    "        argsList.add(serverPort);" +
                    "    }" +
                    "    Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "    gameArgsClass.getMethod(\"setArgsRaw\", new Class[]{String[].class}).invoke(null, new Object[]{argsList.toArray(new String[0])});" +
                    "    Class assetUtilsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.assets.AssetUtils\");" +
                    "    assetUtilsClass.getMethod(\"downloadAssets\", null).invoke(null, null);" +
                    "}"
                );
            }
        });

        return true;
    }

    private boolean findArgsWithWrapper(PatchPool patchPool) throws Exception {
        if (patchPool.getRawClass("net.minecraft.Launcher") == null) {
            return false;
        }

        patchPool.addCtTransformer("net.minecraft.Launcher", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod mainMethod = ctClass.getDeclaredMethod("setParameter");
                mainMethod.insertBefore("" +
                    "Class gameArgsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.GameArgs\");" +
                    "gameArgsClass.getMethod(\"setParam\", new Class[]{String.class, String.class}).invoke(null, new Object[]{$1, $2});" +
                    "Class assetUtilsClass = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.proxy.assets.AssetUtils\");" +
                    "assetUtilsClass.getMethod(\"downloadAssets\", null).invoke(null, null);"
                );
            }
        });

        return true;
    }
}
