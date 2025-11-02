package uk.betacraft.legacyfix.tweaker;

import net.minecraft.launchwrapper.IClassTransformer;

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
            System.err.println("Transformed class " + transformedName);
            return transformed;
        }

        return bytecode;
    }
}
