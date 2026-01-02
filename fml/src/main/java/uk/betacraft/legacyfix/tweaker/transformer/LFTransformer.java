package uk.betacraft.legacyfix.tweaker.transformer;

import net.minecraft.launchwrapper.IClassTransformer;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.tweaker.LFTweaker;

@SuppressWarnings("unused")
public class LFTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytecode) {
        if (LFTweaker.patcher == null) {
            System.err.println("Patches have not been initialised");
            return bytecode;
        }

        byte[] transformed = LFTweaker.patcher.getTransformedClass(transformedName);
        if (transformed != null) {
            return transformed;
        }

        if (bytecode == null) {
            return null;
        }

        for (Transformer transformer : LFTweaker.patcher.getTransformers()) {
            try {
                transformed = transformer.transform(name, bytecode);
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
