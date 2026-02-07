package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import uk.betacraft.legacyfix.LegacyFixLauncher;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

import java.io.File;

public class GameDirPatch extends Patch {
    public GameDirPatch() {
        super("game-dir", "Redirects Minecraft to the intended game directory", true, true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        final CtClass fileClass = patchPool.getRawClass("java.io.File");
        final CtClass fileFilterClass = patchPool.getRawClass("java.io.FileFilter");
        patchPool.addCtTransformer("java.io.File", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtConstructor fileConstructor = ctClass.getDeclaredConstructor(new CtClass[]{Patch.CT_STRING, Patch.CT_STRING});
                fileConstructor.insertBefore("" +
                    "if ($1.equals(System.getenv(\"APPDATA\")) || $1.equals(System.getProperty(\"user.home\"))) {" +
                    "    if ($2.startsWith(\".minecraft/\") || $2.startsWith(\"minecraft/\") || " +
                    "               $2.startsWith(\"Library/Application Support/minecraft\")) {" +
                    "        $1 = null;" +
                    "        Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "        $2 = (String) assetUtils.getMethod(\"getRelativePathToGameDir\", new Class[] {String.class}).invoke(null, new Object[] {$2});" +
                    "    }" +
                    "}"
                );

                CtConstructor fileConstructor2 = ctClass.getDeclaredConstructor(new CtClass[]{fileClass, Patch.CT_STRING});
                fileConstructor2.insertBefore("" +
                    "try {" +
                    "    if ($1.path.contains(\"assets\") && $2.equals(\"skins\")) {" +
                    "        Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "        if (((Boolean) assetUtils.getMethod(\"isExpectedAssetsDir\", new Class[] {String.class}).invoke(null, new Object[] {$1.path})).booleanValue()) {" +
                    "            $1 = (java.io.File) assetUtils.getMethod(\"getCacheDirectory\", null).invoke(null, null);" +
                    "        }" +
                    "    }" +
                    "} catch (Throwable t) { t.printStackTrace(); }"
                );

                CtMethod existsMethod = ctClass.getDeclaredMethod("exists");
                existsMethod.insertAfter("" +
                    "if (!($r)$_) {" +
                    "    try {" +
                    "        if (System.getProperty(\"assets-loaded\", \"false\").equals(\"true\")) {" +
                    "            Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "            Object asset = assetUtils.getMethod(\"getAssetPathFromExpectedPath\", new Class[] {String.class}).invoke(null, new Object[] {$0.path});" +
                    "            if (asset != null) {" +
                    "                return true;" +
                    "            }" +
                    "        }" +
                    "        if ($0.path.endsWith(\".png\")) {" +
                    "            Class patchHelper = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.misc.GameDirPatch\");" +
                    "            java.io.File newFile = (java.io.File) patchHelper.getMethod(\"getIndevMapRenderFromExpectedPath\", new Class[] {java.io.File.class}).invoke(null, new Object[] {$0});" +
                    "            if (newFile != null) {" +
                    "                return newFile.exists();" +
                    "            }" +
                    "        }" +
                    "    } catch (Throwable t) { t.printStackTrace(); }" +
                    "}"
                );

                CtMethod lengthMethod = ctClass.getDeclaredMethod("length");
                lengthMethod.insertAfter("" +
                    "if (($r)$_ == 0L) {" +
                    "    try {" +
                    "        if (System.getProperty(\"assets-loaded\", \"false\").equals(\"true\")) {" +
                    "            Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "            Long size = (Long) assetUtils.getMethod(\"getAssetSizeFromExpectedPath\", new Class[] {String.class}).invoke(null, new Object[] {$0.path});" +
                    "            if (size.longValue() != -1L) {" +
                    "                return size.longValue();" +
                    "            }" +
                    "        }" +
                    "    } catch (Throwable t) { t.printStackTrace(); }" +
                    "}"
                );

                CtMethod listFilesMethod = ctClass.getDeclaredMethod("listFiles");
                listFilesMethod.insertBefore("" +
                    "try {" +
                    "    if ($0.path.contains(\"assets\")) {" +
                    "        Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "        if (((Boolean) assetUtils.getMethod(\"isExpectedAssetsDir\", new Class[] {String.class}).invoke(null, new Object[] {$0.path})).booleanValue()) {" +
                    "            return (java.io.File[]) assetUtils.getMethod(\"getAssetsAsFileArray\", null).invoke(null, null);" +
                    "        }" +
                    "    }" +
                    "} catch (Throwable t) { t.printStackTrace(); }"
                );

                CtMethod listFiles2Method = ctClass.getDeclaredMethod("listFiles", new CtClass[]{fileFilterClass});
                listFiles2Method.insertBefore("" +
                    "try {" +
                    "    if ($0.path.contains(\"assets\")) {" +
                    "        Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "        if (((Boolean) assetUtils.getMethod(\"isExpectedAssetsDir\", new Class[] {String.class}).invoke(null, new Object[] {$0.path})).booleanValue()) {" +
                    "            return (java.io.File[]) assetUtils.getMethod(\"getAssetsAsFileArray\", null).invoke(null, null);" +
                    "        }" +
                    "    }" +
                    "} catch (Throwable t) { t.printStackTrace(); }"
                );

                CtMethod isDirectoryMethod = ctClass.getDeclaredMethod("isDirectory");
                isDirectoryMethod.insertAfter("" +
                    "if (!($r)$_) {" +
                    "    try {" +
                    "        if ($0.path.contains(\"assets\")) {" +
                    "            Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "            return ((Boolean) assetUtils.getMethod(\"isExpectedAssetsDir\", new Class[] {String.class}).invoke(null, new Object[] {$0.path})).booleanValue();" +
                    "        }" +
                    "    } catch (Throwable t) { t.printStackTrace(); }" +
                    "}"
                );
            }
        });

        patchPool.addCtTransformer("java.io.FileInputStream", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtConstructor fileInputStreamConstructor = ctClass.getDeclaredConstructor(new CtClass[]{fileClass});
                fileInputStreamConstructor.insertBefore("" +
                    "try {" +
                    "    if (System.getProperty(\"assets-loaded\", \"false\").equals(\"true\")) {" +
                    "        Class assetUtils = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
                    "        String asset = (String) assetUtils.getMethod(\"getAssetPathFromExpectedPath\", new Class[] {String.class}).invoke(null, new Object[] {$1.getPath()});" +
                    "        if (asset != null) {" +
                    "            $1 = new java.io.File(asset);" +
                    "        }" +
                    "    }" +
                    "} catch (Throwable t) { t.printStackTrace(); }"
                );
            }
        });

        patchPool.addCtTransformer("java.io.FileOutputStream", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                CtConstructor fileOutputStreamConstructor = ctClass.getDeclaredConstructor(new CtClass[]{fileClass});
                fileOutputStreamConstructor.insertBefore(
                    "try {" +
                        "    Class patchHelper = Thread.currentThread().getContextClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.misc.GameDirPatch\");" +
                        "    java.io.File newFile = (java.io.File) patchHelper.getMethod(\"getIndevMapRenderFromExpectedPath\", new Class[] {java.io.File.class}).invoke(null, new Object[] {$1});" +
                        "    if (newFile != null) {" +
                        "        $1 = newFile;" +
                        "    }" +
                        "} catch (Throwable t) { t.printStackTrace(); }"
                );
            }
        });
    }

    @SuppressWarnings("unused")
    public static File getIndevMapRenderFromExpectedPath(File file) {
        String fileName = file.getName();
        File expectedFile = new File(new File(System.getProperty("user.home", ".")), fileName).getAbsoluteFile();

        if (!fileName.startsWith("mc_map_") ||
            !fileName.endsWith(".png") ||
            !expectedFile.getPath().equals(file.getAbsoluteFile().getPath())
        ) {
            return null;
        }

        return new File(LegacyFixLauncher.getScreenshotsDir(), fileName).getAbsoluteFile();
    }
}
