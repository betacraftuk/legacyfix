package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.LegacyFixLauncher;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class FramePatch extends Patch {
    public FramePatch() {
        super("lwjgl-frame", "Patches LWJGL Frame for title and resolution", true);
    }

    @Override
    public void apply(final PatchPool patchPool) throws Exception {
        try {
            Icons.loadIcons((String) Agent.getSettings().get("lf.icon"));
        } catch (Exception e) {
            Logger.error(this, e);
        }

        CtClass displayClass = patchPool.getClass("org.lwjgl.opengl.Display");
        if (displayClass.isFrozen()) {
            displayClass.defrost();
        }

        CtMethod setTitleMethod = displayClass.getDeclaredMethod("setTitle", new CtClass[]{CT_STRING});
        setTitleMethod.insertBefore("" +
            // Title
            "Class legacyfix = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
            "$1 = (String) legacyfix.getMethod(\"getFrameName\", null).invoke(null, null);" +

            // Resizable
            "org.lwjgl.opengl.Display.setResizable(true);" +

            // 16x16 icon
            "java.lang.reflect.Field f16 = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.lwjgl.FramePatch$Icons\").getDeclaredField(\"pixels16\");" +
            "f16.setAccessible(true);" +
            "java.nio.ByteBuffer pix16 = f16.get(null);" +

            // 32x32 icon
            "java.lang.reflect.Field f32 = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.lwjgl.FramePatch$Icons\").getDeclaredField(\"pixels32\");" +
            "f32.setAccessible(true);" +
            "java.nio.ByteBuffer pix32 = f32.get(null);" +

            // Setting the icon
            "if (pix16 != null && pix32 != null) {" +
            "    org.lwjgl.opengl.Display.setIcon(new java.nio.ByteBuffer[] {pix16, pix32});" +
            "}"
        );

        CtClass displayModeClass = patchPool.getClass("org.lwjgl.opengl.DisplayMode");
        CtConstructor displayModeConstructor = displayModeClass.getDeclaredConstructor(new CtClass[]{CT_INT, CT_INT});
        displayModeConstructor.insertBefore("" +
            "Class legacyfix = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
            "$1 = ((Integer) legacyfix.getMethod(\"getWidth\", null).invoke(null, null)).intValue();" +
            "$2 = ((Integer) legacyfix.getMethod(\"getHeight\", null).invoke(null, null)).intValue();"
        );

        patchPool.patchClass(displayModeClass);

        // Make 13w16a-13w24b honor custom width & height
        CtClass minecraftMainClass = patchPool.getClass(LegacyFixLauncher.getValue("mainClass", "net.minecraft.client.main.Main"));
        if (minecraftMainClass == null) {
            return;
        }

        CtMethod mainMethod = minecraftMainClass.getDeclaredMethod("main");
        mainMethod.instrument(new ExprEditor() {
            public void edit(NewExpr m) {
                try {
                    CtConstructor minecraftConstructor = m.getConstructor();
                    CtClass[] parameterTypes = minecraftConstructor.getParameterTypes();

                    boolean isMinecraft = false;
                    for (CtClass proxyClass : parameterTypes) {
                        // Minecraft class constructor in affected versions takes a Proxy object
                        if ("java.net.Proxy".equals(proxyClass.getName())) {
                            isMinecraft = true;
                            break;
                        }
                    }

                    if (!isMinecraft) {
                        return;
                    }

                    for (int i = 0; i < parameterTypes.length; i++) {
                        CtClass intClass = parameterTypes[i];
                        if (!"int".equals(intClass.getName())) {
                            continue;
                        }

                        minecraftConstructor.insertBefore("" +
                            "Class legacyfix = java.lang.Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.LegacyFixLauncher\");" +
                            "$" + (i + 1) + " = ((Integer) legacyfix.getMethod(\"getWidth\", null).invoke(null, null)).intValue();" +
                            "$" + (i + 2) + " = ((Integer) legacyfix.getMethod(\"getHeight\", null).invoke(null, null)).intValue();"
                        );

                        CtClass minecraftClass = minecraftConstructor.getDeclaringClass();
                        patchPool.patchClass(minecraftClass);
                        break;
                    }
                } catch (Throwable t) {
                    Logger.error("lwjgl-frame", t);
                }
            }
        });
    }

    public static class Icons {
        static ByteBuffer pixels16 = null;
        static ByteBuffer pixels32 = null;

        public static void loadIcons(String iconPath) throws IOException {
            if (iconPath != null) {
                File iconFile = new File(iconPath);

                if (iconFile.exists() && iconFile.isFile()) {
                    pixels32 = getIconForLWJGL(new FileInputStream(iconFile), 32);
                    pixels16 = getIconForLWJGL(new FileInputStream(iconFile), 16);
                } else {
                    Logger.error("No icon found at given path: " + iconPath);

                    pixels16 = getIconForLWJGL(Agent.class.getResourceAsStream("/favicon.png"), 16);
                    pixels32 = getIconForLWJGL(Agent.class.getResourceAsStream("/favicon.png"), 32);
                }
            } else {
                pixels16 = getIconForLWJGL(Agent.class.getResourceAsStream("/favicon.png"), 16);
                pixels32 = getIconForLWJGL(Agent.class.getResourceAsStream("/favicon.png"), 32);
            }
        }

        private static ByteBuffer getIconForLWJGL(InputStream stream, int resolution) throws IOException {
            final Image read = ImageIO.read(stream).getScaledInstance(resolution, resolution, Image.SCALE_SMOOTH);

            BufferedImage bufImg = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);

            Graphics g = bufImg.getGraphics();
            g.drawImage(read, 0, 0, null);
            g.dispose();

            final int[] rgb = bufImg.getRGB(0, 0, resolution, resolution, null, 0, resolution);
            final ByteBuffer allocate = ByteBuffer.allocate(4 * rgb.length);

            for (final int n : rgb) {
                allocate.putInt(n << 8 | (n >> 24 & 0xFF));
            }

            allocate.flip();
            return allocate;
        }
    }
}