package uk.betacraft.legacyfix.util;

import javassist.ClassPool;
import javassist.CtClass;
import uk.betacraft.legacyfix.LFLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressWarnings("unused")
public class ClassDumper {
    public static void dumpClasses(String... classes) {
        for (String className : classes) {
            dumpClass(className);
        }
    }

    public static void dumpClass(String className) {
        try {
            CtClass ctClass = ClassPool.getDefault().get(className);

            byte[] bytecode = ctClass.toBytecode();
            String filePath = className.replace('.', File.separatorChar) + ".class";
            File outputFile = new File("lf_class_dumps", filePath);

            File parentDir = outputFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            writeToFile(outputFile, bytecode);
        } catch (Exception e) {
            LFLogger.error("Failed to dump class: " + className, e);
        }
    }

    private static void writeToFile(File file, byte[] data) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
}
