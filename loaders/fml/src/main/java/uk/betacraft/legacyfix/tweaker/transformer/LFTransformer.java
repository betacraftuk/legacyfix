package uk.betacraft.legacyfix.tweaker.transformer;

import javassist.ClassPool;
import javassist.CtClass;
import net.minecraft.launchwrapper.IClassTransformer;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.tweaker.LFTweaker;

import java.util.List;

@SuppressWarnings("unused")
public class LFTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytecode) {
        if (bytecode == null) {
            return null;
        }

        if (LFTweaker.patcher == null) {
            System.err.println("Patcher not initialized yet, skipping transformation for " + name);
            return bytecode;
        }

        if (name.startsWith("javassist")) {
            return bytecode;
        }

        List<CtTransformer> ctTransformers = LFTweaker.patcher.getCtTransformers().get(name);
        if (ctTransformers != null && !ctTransformers.isEmpty()) {
            try {
                ClassPool ctPool = new ClassPool(true);
                CtClass ctClass = ctPool.makeClass(new java.io.ByteArrayInputStream(bytecode));

                for (CtTransformer ctTransformer : ctTransformers) {
                    ctTransformer.transform(ctClass);
                }

                if (ctClass.isModified()) {
                    bytecode = ctClass.toBytecode();
                }

                ctClass.detach();
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply CtTransformer on class \"" + name + "\"", e);
            }
        }

        bytecode = LFTweaker.patcher.applyTransformers(name, bytecode);

        return bytecode;
    }
}
