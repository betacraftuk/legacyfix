package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchHelper;

/**
 * Patch for the unresponsive "Open texture pack folder" button in versions before 1.2-pre on Linux and macOS
 */
public class TexturePackFolderPatch extends Patch {
    public TexturePackFolderPatch() {
        super("texturePackButton", "Fixes the open texture pack folder button", true);
    }

    @Override
    public void apply(final Instrumentation inst) throws Exception {
        CtClass sysClass = pool.get("org.lwjgl.Sys");

        CtMethod openURLMethod = sysClass.getDeclaredMethod("openURL", new CtClass[]{PatchHelper.stringClass});

        openURLMethod.insertBefore(
                "if ($1 != null && $1.indexOf(\"file://\") == 0) {" +
                        "    String txpfolder = $1.substring(7);" +
                        "    try {" +
                        "        Class desktopClass = Class.forName(\"java.awt.Desktop\");" +
                        "        Object desktop = desktopClass.getMethod(\"getDesktop\", null).invoke((Object) null, null);" +
                        "        Class[] browseParameterTypes = new Class[1];" +
                        "        browseParameterTypes[0] = java.net.URI.class;" +
                        "        Object[] browseInvokeParameters = new Object[1];" +
                        "        browseInvokeParameters[0] = (new java.io.File(txpfolder)).toURI();" +
                        "        desktopClass.getMethod(\"browse\", browseParameterTypes).invoke(desktop, browseInvokeParameters);" +
                        "        return true;" +
                        "    } catch (Throwable t) {" +
                        "        t.printStackTrace();" + // if failed, don't return and have a shot at the Sys.openURL method (vanilla behavior since 1.2-pre)
                        "    }" +
                        "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(sysClass.getName()), sysClass.toBytecode()));
    }
}
