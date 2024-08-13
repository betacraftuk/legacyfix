package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;

/**
 * Patch for unresponsive "Open texture pack folder" button in versions before 1.2-pre on Linux and macOS
 */
public class TexturePackFolderPatch extends Patch {
	public TexturePackFolderPatch() {
		super("texturePackButton", "Patch open texture pack folder button");
	}

	@Override
	public void apply(final Instrumentation inst) throws Exception {
        CtClass clazz;
        CtMethod method;
        CtClass string = pool.get("java.lang.String");

        clazz = pool.get("org.lwjgl.Sys");
        method = clazz.getDeclaredMethod("openURL", new CtClass[] {string});
        method.insertBefore(
            "if ($1 != null && $1.indexOf(\"file://\") == 0) {" +
            "	String txpfolder = $1.substring(7);" +
            "	try {" +
            "		Class desktopClass = Class.forName(\"java.awt.Desktop\");" +
            "		Object desktop = desktopClass.getMethod(\"getDesktop\", null).invoke((Object) null, null);" +
            "       Class[] browseParameterTypes = new Class[1];" +
            "       browseParameterTypes[0] = java.net.URI.class;" +
            "       Object[] browseInvokeParameters = new Object[1];" +
            "       browseInvokeParameters[0] = (new java.io.File(txpfolder)).toURI();" +
            "		desktopClass.getMethod(\"browse\", browseParameterTypes).invoke(desktop, browseInvokeParameters);" +
            "		return true;" +
            "	} catch (Throwable t) {" +
            "		t.printStackTrace();" + // if failed, don't return and have a shot at the Sys.openURL method (vanilla behavior since 1.2-pre)
            "	}" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(clazz.getName()), clazz.toBytecode()));
	}

    @Override
    public boolean shouldApply() {
        return true;
    }
}
