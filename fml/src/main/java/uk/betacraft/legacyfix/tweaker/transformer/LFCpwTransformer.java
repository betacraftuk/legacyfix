package uk.betacraft.legacyfix.tweaker.transformer;

import cpw.mods.fml.relauncher.IClassTransformer;
import uk.betacraft.legacyfix.tweaker.CpwTweaker;
import uk.betacraft.legacyfix.patch.api.Transformer;

@SuppressWarnings("unused")
public class LFCpwTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (CpwTweaker.patcher == null) {
            System.err.println("Patches have not been initialised");
            return bytes;
        }

        byte[] transformed = CpwTweaker.patcher.getTransformedClass(name);
        if (transformed != null) {
            return transformed;
        }

        if (bytes == null) {
            return null;
        }

        for (Transformer transformer : CpwTweaker.patcher.getTransformers()) {
            try {
                transformed = transformer.transform(name, bytes);
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
