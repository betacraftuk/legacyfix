package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class TexturePackFolderPatch extends Patch {
    public TexturePackFolderPatch() {
        super("texture-pack-folder", "Fixes the \"Open Texture Pack Folder\" button", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("org.lwjgl.Sys", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtMethod openURLMethod = ctClass.getDeclaredMethod("openURL", new CtClass[]{Patch.CT_STRING});
                openURLMethod.insertBefore("" +
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
                    "        t.printStackTrace();" +
                    "    }" +
                    "}"
                );
            }
        });
    }
}
