package uk.betacraft.legacyfix.tweaker.transformer;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import net.minecraft.launchwrapper.IClassTransformer;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Transformer;
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

        List<CtTransformer> ctTransformers = LFTweaker.patcher.getCtTransformers().get(transformedName);
        if (ctTransformers != null && !ctTransformers.isEmpty()) {
            try {
                ClassPool ctPool = new ClassPool(true);
                ctPool.appendClassPath(new ByteArrayClassPath(transformedName, bytecode));
                CtClass ctClass = ctPool.get(transformedName);

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

        for (Transformer transformer : LFTweaker.patcher.getTransformers()) {
            try {
                byte[] transformed = transformer.transform(name, bytecode);
                if (transformed != null) {
                    bytecode = transformed;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply transformer on class \"" + name + "\"", e);
            }
        }

        return bytecode;
    }
}
