package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import uk.betacraft.legacyfix.LegacyFixAgent;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class GameDirPatch extends Patch {
    public GameDirPatch() {
        super("gamedirpatch", "Redirects Minecraft to the intended game directory", true);
    }

    public void apply(Instrumentation inst) throws PatchException, Exception {
        CtClass fileClass = pool.get("java.io.File");

        CtConstructor fileConstructor = fileClass.getDeclaredConstructor(
                new CtClass[]{PatchHelper.stringClass, PatchHelper.stringClass});

        // @formatter:off
        fileConstructor.insertBefore(
            "if ($1.equals(System.getenv(\"APPDATA\")) || $1.equals(System.getProperty(\"user.home\"))) {" +
            "   if ($2.equals(\".minecraft/\") || $2.equals(\"minecraft/\") || " +
            "               $2.equals(\"Library/Application Support/minecraft\")) {" +
            "       $1 = null;" +
            "       $2 = \"" + LegacyFixAgent.getGameDir() + "\";" +
            "   }" +
            "}"
        );

        CtMethod existsMethod = fileClass.getDeclaredMethod("exists");
        existsMethod.insertAfter(
            "if (!($r)$_) {" +
            "    try {" +
            "        if (System.getProperty(\"assets-loaded\", \"false\").equals(\"true\")) {" +
            "            Class assetUtils = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
            "            Object asset = assetUtils.getMethod(\"getAssetPathFromExpectedPath\", new Class[] {String.class}).invoke(null, new Object[] {$0.path});" +
            "            if (asset != null) {" +
            //"                System.out.println($0.path);" +
            //"                System.out.println(asset);" +
            "                return true;" +
            "            }" +
            "        }" +
            "    } catch (Throwable t) { t.printStackTrace(); }" +
            "}"
        );

        CtMethod lengthMethod = fileClass.getDeclaredMethod("length");
        lengthMethod.insertAfter(
            "if (($r)$_ == 0L) {" +
            "    try {" +
            "        if (System.getProperty(\"assets-loaded\", \"false\").equals(\"true\")) {" +
            "            Class assetUtils = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
            "            Long size = (Long) assetUtils.getMethod(\"getAssetSizeFromExpectedPath\", new Class[] {String.class}).invoke(null, new Object[] {$0.path});" +
            "            if (size.longValue() != -1L) {" +
            //"                System.out.println(size);" +
            "                return size.longValue();" +
            "            }" +
            "        }" +
            "    } catch (Throwable t) { t.printStackTrace(); }" +
            "}"
        );

        CtMethod listFilesMethod = fileClass.getDeclaredMethod("listFiles");
        listFilesMethod.insertBefore(
            "try {" +
            "    if ($0.path.contains(\"assets\")) {" +
            "        Class assetUtils = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
            "        if (((Boolean) assetUtils.getMethod(\"isExpectedAssetsDir\", new Class[] {String.class}).invoke(null, new Object[] {$0.path})).booleanValue()) {" +
            "            return (java.io.File[]) assetUtils.getMethod(\"getAssetsAsFileArray\", null).invoke(null, null);" +
            "        }" +
            "    }" +
            "} catch (Throwable t) { t.printStackTrace(); }"
        );

        CtMethod listFiles2Method = fileClass.getDeclaredMethod("listFiles", new CtClass[] {pool.get("java.io.FileFilter")});
        listFiles2Method.insertBefore(
            "try {" +
            "    if ($0.path.contains(\"assets\")) {" +
            "        Class assetUtils = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
            "        if (((Boolean) assetUtils.getMethod(\"isExpectedAssetsDir\", new Class[] {String.class}).invoke(null, new Object[] {$0.path})).booleanValue()) {" +
            "            return (java.io.File[]) assetUtils.getMethod(\"getAssetsAsFileArray\", null).invoke(null, null);" +
            "        }" +
            "    }" +
            "} catch (Throwable t) { t.printStackTrace(); }"
        );

        CtMethod isDirectoryMethod = fileClass.getDeclaredMethod("isDirectory");
        isDirectoryMethod.insertAfter(
            "if (!($r)$_) {" +
            "    try {" +
            "        if ($0.path.contains(\"assets\")) {" +
            "            Class assetUtils = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
            "            return ((Boolean) assetUtils.getMethod(\"isExpectedAssetsDir\", new Class[] {String.class}).invoke(null, new Object[] {$0.path})).booleanValue();" +
            "        }" +
            "    } catch (Throwable t) { t.printStackTrace(); }" +
            "}"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(fileClass.getName()), fileClass.toBytecode()));

        CtClass fileInputStreamClass = pool.get("java.io.FileInputStream");

        CtConstructor fileInputStreamConstructor = fileInputStreamClass.getDeclaredConstructor(
                new CtClass[]{fileClass});

        fileInputStreamConstructor.insertBefore(
            "try {" +
            "    if (System.getProperty(\"assets-loaded\", \"false\").equals(\"true\")) {" +
            "        Class assetUtils = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.util.AssetUtils\");" +
            "        String asset = (String) assetUtils.getMethod(\"getAssetPathFromExpectedPath\", new Class[] {String.class}).invoke(null, new Object[] {$1.getPath()});" +
            "        if (asset != null) {" +
            //"            System.out.println($0.path);" +
            //"            System.out.println(asset);" +
            "            $1 = new java.io.File(asset);" +
            "        }" +
            "    }" +
            "} catch (Throwable t) { t.printStackTrace(); }"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(fileInputStreamClass.getName()), fileInputStreamClass.toBytecode()));
    }
}
