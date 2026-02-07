package uk.betacraft.legacyfix.tweaker.transformer;

import cpw.mods.fml.relauncher.IClassTransformer;
import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.tweaker.CpwTweaker;
import uk.betacraft.legacyfix.patch.api.Transformer;

import java.util.List;

@SuppressWarnings("unused")
public class LFCpwTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        if (CpwTweaker.patcher == null) {
            System.err.println("Patcher not initialized yet, skipping transformation for " + name);
            return bytes;
        }

        List<CtTransformer> ctTransformers = CpwTweaker.patcher.getCtTransformers().get(name);
        if (ctTransformers != null && !ctTransformers.isEmpty()) {
            try {
                ClassPool ctPool = new ClassPool(true);
                ctPool.appendClassPath(new ByteArrayClassPath(name, bytes));
                CtClass ctClass = ctPool.get(name);

                for (CtTransformer ctTransformer : ctTransformers) {
                    ctTransformer.transform(ctClass);
                }

                if (ctClass.isModified()) {
                    bytes = ctClass.toBytecode();
                }

                ctClass.detach();
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply CtTransformer on class \"" + name + "\"", e);
            }
        }

        for (Transformer transformer : CpwTweaker.patcher.getTransformers()) {
            try {
                byte[] transformed = transformer.transform(name, bytes);
                if (transformed != null) {
                    bytes = transformed;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply transformer on class \"" + name + "\"", e);
            }
        }

        return bytes;
    }
}
