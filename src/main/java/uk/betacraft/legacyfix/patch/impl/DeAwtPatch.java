package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;
import uk.betacraft.legacyfix.util.IconUtils;

public class DeAwtPatch extends Patch {
    private String canvasClassName;
    private Exception thrown;

    public DeAwtPatch() {
        super("deawt", "Forces the game to use LWJGL's Display instead of AWT's Frame", true);
    }

    @Override
    public void apply(final Instrumentation inst) throws Exception  {
        // Attempt to load icons
        try {
            IconUtils.loadIcons((String) LegacyFixAgent.getSettings().get("icon"));
        } catch (Exception e) {
            LFLogger.error(this, e);
        }

        // Find the applet class
        CtClass appletClass = PatchHelper.findMinecraftAppletClass(pool);

        if (appletClass == null)
            throw new PatchException("No applet class could be found");

        final String appletClassName = appletClass.getName();

        // Find the main Minecraft class
        CtClass minecraftClass = PatchHelper.findMinecraftClass(pool);

        if (minecraftClass == null)
            throw new PatchException("No main Minecraft class could be found");

        // Find the appletMode field
        CtField appletModeField = PatchHelper.findAppletModeField(pool);

        CtMethod initMethod = appletClass.getDeclaredMethod("init");

        // @formatter:off
        initMethod.insertAfter(
            // Dispose of all AWT/Swing components
            "java.awt.Component parent = $0;" +
            "while (parent != null) {" +
            "    parent.setVisible(false);" +
            "    if (parent instanceof java.awt.Frame) {" +
            "        ((java.awt.Frame)parent).dispose();" +
            "    }" +
            "    parent = parent.getParent();" +
            "}" +
            // Set 'appletMode' to 'false' so the game handles LWJGL Display correctly
            "$0." + minecraftClass.getName() + "." + appletModeField.getName() + " = false;"
        );
        // @formatter:on

        // Take the canvas class name to later edit out its removeNotify() method
        // The method relies on AWT/Swing components, so it has to be hijacked

        initMethod.instrument(new ExprEditor() {
            public void edit(NewExpr m) throws CannotCompileException {
                try {
                    if (m.getConstructor().getLongName().contains("(" + appletClassName + ")") &&
                            m.getSignature().equals("(L" + appletClassName.replace(".", "/") + ";)V")) {
                        canvasClassName = m.getClassName();
                        LFLogger.info("Found Canvas class name: " + canvasClassName);
                    }
                } catch (NotFoundException e) {
                    DeAwtPatch.this.thrown = e;
                }
            }
        });

        if (this.thrown != null)
            throw this.thrown;

        // Redefine the applet class
        inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(appletClassName), appletClass.toBytecode())});

        // Find ways to hook into dynamic width/height changing
        // sacred code don't touch (!!!)
        CtClass guiScreenClass = null;
        CtMethod guiScreenInitMethod = null;
        CtField guiScreenField = null;

        CtField[] minecraftClassFields = minecraftClass.getDeclaredFields();

        for (CtField field : minecraftClassFields) {
            CtMethod[] methods = field.getType().getDeclaredMethods();

            for (CtMethod method : methods) {
                CtClass[] params = method.getParameterTypes();
                if (params.length == 3 && params[0].getName().equals(minecraftClass.getName()) && params[1].getName().equals("int") && params[2].getName().equals("int")) {

                    guiScreenClass = field.getType();
                    guiScreenInitMethod = method;
                    guiScreenField = field;

                    LFLogger.info("Found match for GuiScreen: " + guiScreenClass.getName());
                    break;
                }
            }

            if (guiScreenField != null)
                break;
        }

        // Find InGameHud
        CtClass inGameHudClass = null;
        CtField inGameHudField = null;

        for (CtField field : minecraftClassFields) {
            CtConstructor[] constrs = field.getType().getDeclaredConstructors();

            for (CtConstructor constr : constrs) {
                CtClass[] params = constr.getParameterTypes();
                if (params.length == 3 && params[0].getName().equals(minecraftClass.getName()) && params[1].getName().equals("int") && params[2].getName().equals("int")) {

                    inGameHudClass = field.getType();
                    inGameHudField = field;

                    LFLogger.info("Found match for InGameHud: " + field.getName() + " / " + inGameHudClass.getName());
                    break;
                }
            }

            if (inGameHudField != null)
                break;
        }

        // Find resolution fields in InGameHud class
        CtField[] inGameHudResFields = new CtField[] {null, null};

        if (inGameHudClass != null) {

            // We take for granted that first two int fields are: width & height
            int intOccurences = 0;

            for (CtField field : inGameHudClass.getDeclaredFields()) {
                String className = field.getType().getName();

                if (className.equals("int") && intOccurences < 2) {
                    LFLogger.info("Found InGameHud resolution field (" + Integer.toString(intOccurences) + "): " + field.getName());

                    inGameHudResFields[intOccurences] = field;

                    intOccurences++;

                    if (intOccurences > 1)
                        break;
                }
            }
        }

        // Find the resolution fields in Minecraft class
        CtField[] minecraftResFields = new CtField[] {null, null};

        // We take for granted that first two int fields are: width & height
        int intOccurences = 0;

        for (CtField field : minecraftClass.getDeclaredFields()) {
            String className = field.getType().getName();

            if (className.equals("int") && intOccurences < 2) {
                LFLogger.info("Found Minecraft resolution field (" + Integer.toString(intOccurences) + "): " + field.getName());

                minecraftResFields[intOccurences] = field;

                intOccurences++;

                if (intOccurences > 1)
                    break;
            }
        }

        // Make ad-hoc thread to monitor changes of resolution
        // If resolution is changed, update it within the Minecraft class
        CtClass threadClass = pool.get("java.lang.Thread");
        CtClass resizeThreadClass = pool.makeClass("uk.betacraft.legacyfix.ResizeThread", threadClass);

        CtField resizeThreadMinecraftField = CtField.make("public final " + minecraftClass.getName() + " mc;", resizeThreadClass);
        resizeThreadClass.addField(resizeThreadMinecraftField);

        CtConstructor resizeThreadConstructor = new CtConstructor(new CtClass[] {minecraftClass}, resizeThreadClass);
        // @formatter:off
        resizeThreadConstructor.setBody(
            "{" +
            "    $0.mc = $1;" +
            "}"
        );

        resizeThreadClass.addConstructor(resizeThreadConstructor);

        String widthFieldName = minecraftResFields[0].getName();
        String heightFieldName = minecraftResFields[1].getName();

        String hudWidthFieldName = inGameHudResFields[0] != null ? inGameHudResFields[0].getName() : null;
        String hudHeightFieldName = inGameHudResFields[1] != null ? inGameHudResFields[1].getName() : null;

        CtMethod resizeThreadRunMethod = CtMethod.make("public void run() {}", resizeThreadClass);
        resizeThreadRunMethod.setBody(
            "{" +
            (guiScreenField != null ?
            "    java.lang.reflect.Field guiscreen = ClassLoader.getSystemClassLoader().loadClass(\"" + minecraftClass.getName() + "\").getDeclaredField(\"" + guiScreenField.getName() + "\");" +
            "    guiscreen.setAccessible(true);" : ""
            ) +

            (hudWidthFieldName != null ? 
            "    java.lang.reflect.Field hud = ClassLoader.getSystemClassLoader().loadClass(\"" + minecraftClass.getName() + "\").getDeclaredField(\"" + inGameHudField.getName() + "\");" +
            "    hud.setAccessible(true);" +
            "    java.lang.reflect.Field hudWidth = ClassLoader.getSystemClassLoader().loadClass(\"" + inGameHudClass.getName() + "\").getDeclaredField(\"" + hudWidthFieldName + "\");" +
            "    hudWidth.setAccessible(true);" +
            "    java.lang.reflect.Field hudHeight = ClassLoader.getSystemClassLoader().loadClass(\"" + inGameHudClass.getName() + "\").getDeclaredField(\"" + hudHeightFieldName + "\");" +
            "    hudHeight.setAccessible(true);" : ""
            ) +

            "    java.lang.reflect.Field mcwidth = ClassLoader.getSystemClassLoader().loadClass(\"" + minecraftClass.getName() + "\").getDeclaredField(\"" + widthFieldName + "\");" +
            "    mcwidth.setAccessible(true);" +
            "    java.lang.reflect.Field mcheight = ClassLoader.getSystemClassLoader().loadClass(\"" + minecraftClass.getName() + "\").getDeclaredField(\"" + heightFieldName + "\");" +
            "    mcheight.setAccessible(true);" +

            "    while (!org.lwjgl.opengl.Display.isCreated()) {}" +
            "    while (org.lwjgl.opengl.Display.isCreated()) {" +
            "        int mcWidth = mcwidth.getInt($0.mc);" +
            "        int mcHeight = mcheight.getInt($0.mc);" +
            "        if ((org.lwjgl.opengl.Display.getWidth() != mcWidth || org.lwjgl.opengl.Display.getHeight() != mcHeight)) {" +

            "            int xtoset = org.lwjgl.opengl.Display.getWidth();" +
            "            int ytoset = org.lwjgl.opengl.Display.getHeight();" +

            "            if (xtoset <= 0) {xtoset = 1;}" +
            "            if (ytoset <= 0) {ytoset = 1;}" +
            "            mcwidth.setInt($0.mc, xtoset);" +
            "            mcheight.setInt($0.mc, ytoset);" +

            (hudWidthFieldName != null ? 
            "            " + inGameHudClass.getName() + " hudinstance = (" + inGameHudClass.getName() + ") hud.get($0.mc);" +
            "            int hudx = xtoset;" +
            "            int hudy = ytoset;" +
            "            int factor = 1;" +
            "            for(; factor < 1000 && hudx / (factor + 1) >= 320 && hudy / (factor + 1) >= 240; factor++) { }" +
            "            hudx = (int)Math.ceil((double)hudx / (double)factor);" +
            "            hudy = (int)Math.ceil((double)hudy / (double)factor);" +
            "            hudWidth.setInt(hudinstance, hudx);" +
            "            hudHeight.setInt(hudinstance, hudy);" : ""
            ) +

            (guiScreenField != null ?
            "            " + guiScreenClass.getName() + " gsinstance = (" + guiScreenClass.getName() + ") guiscreen.get($0.mc);" +
            "            if (gsinstance != null) {" +
            "                int x = xtoset;" +
            "                int y = ytoset;" +
            "                for(xtoset = 1; x / (xtoset + 1) >= 320 && y / (xtoset + 1) >= 240; ++xtoset) {}" +
            "                x /= xtoset;" +
            "                y /= xtoset;" +
            "                gsinstance." + guiScreenInitMethod.getName() + "($0.mc, x, y);" +
            "            }" : ""
            ) +

            "        }" +
            "    }" +
            "}"
        );
        // @formatter:on

        resizeThreadClass.addMethod(resizeThreadRunMethod);

        resizeThreadClass.toClass(pool.getClassLoader(), pool.getClassLoader().getClass().getProtectionDomain());

        // Initialize the ResizeThread class, so that it can be found by the game
        Class.forName("uk.betacraft.legacyfix.ResizeThread", true, pool.getClassLoader());

        // deAWT main Minecraft class

        if (minecraftClass.isFrozen())
            minecraftClass.defrost();

        // Hook the resolution thread into the Minecraft.run() method
        CtMethod minecraftRunMethod = minecraftClass.getMethod("run", "()V");
        minecraftRunMethod.insertBefore(
            "new uk.betacraft.legacyfix.ResizeThread($0).start();"
        );

        CtConstructor minecraftConstructor = minecraftClass.getConstructors()[0];

        // Nullify Canvas/MinecraftApplet

        // The typical formula of a Minecraft constructor goes like this:
        //  Component, Canvas, MinecraftApplet, int, int, boolean
        CtClass[] paramTypes = minecraftConstructor.getParameterTypes();

        for (int i = 0; i < paramTypes.length; i++) {
            String className = paramTypes[i].getName();

            // If we're at int already, it's done
            if (className.equals("int"))
                break;

            // Nullify Canvas
            if (className.equals("java.awt.Canvas") ||
                    // Only nullify MinecraftApplet if it's not a Classic version
                    ((!appletClassName.startsWith("com.mojang") && pool.getOrNull("com.a.a.a") == null) 
                    && className.equals(appletClassName))) {

                minecraftConstructor.insertBefore("$" + Integer.toString(i+1) + " = null;");
            }
        }

        byte[] minecraftClassBytes = minecraftClass.toBytecode();

        minecraftClass.defrost();

        inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(minecraftClass.getName()), minecraftClassBytes)});

        // deAWT Canvas
        // Stop all calls from the canvas when it gets removed
        canvasClassName = canvasClassName.replace("/", ".");
        CtClass canvasClass = pool.get(canvasClassName);
        CtMethod canvasRemoveNotifyMethod = canvasClass.getDeclaredMethod("removeNotify");
        canvasRemoveNotifyMethod.setBody(
            "{" +
            "    super.removeNotify();" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(canvasClassName), canvasClass.toBytecode())});

        // Hooks for LWJGL to set title, icons, and resizable status
        // and a part of Apple Silicon color patch
        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");
        CtMethod setTitleMethod = displayClass.getDeclaredMethod("setTitle", new CtClass[] {PatchHelper.stringClass});

        // On init
        // @formatter:off
        setTitleMethod.insertBefore(
            // Title
            "$1 = \"" + LegacyFixAgent.getSettings().get("frameName") + "\";" +

            // Resizable
            "org.lwjgl.opengl.Display.setResizable(true);" +

            // 16x16 icon
            "java.lang.reflect.Field f16 = java.lang.ClassLoader.getSystemClassLoader()" +
            "   .loadClass(\"uk.betacraft.legacyfix.util.IconUtils\").getDeclaredField(\"pixels16\");" +
            "f16.setAccessible(true);" +
            "java.nio.ByteBuffer pix16 = f16.get(null);" +

            // 32x32 icon
            "java.lang.reflect.Field f32 = java.lang.ClassLoader.getSystemClassLoader()" +
            "   .loadClass(\"uk.betacraft.legacyfix.util.IconUtils\").getDeclaredField(\"pixels32\");" +
            "f32.setAccessible(true);" +
            "java.nio.ByteBuffer pix32 = f32.get(null);" +

            // Setting the icon
            "org.lwjgl.opengl.Display.setIcon(new java.nio.ByteBuffer[] {pix16, pix32});"
        );

        // On tick - couldn't really hook anywhere else, this looks like a safe spot
        CtMethod isCloseRequestedMethod = displayClass.getDeclaredMethod("isCloseRequested");

        isCloseRequestedMethod.insertBefore(
            "if (org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER).contains(\"Apple M\")) {" + 
            "   org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB);" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode())});
    }
}
