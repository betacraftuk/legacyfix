package uk.betacraft.legacyfix.patch.impl.lwjgl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class DisplayListsPatch extends Patch {
    // Set to 202800 to avoid issues with Extreme render distance mods (32 chunks)
    private static final int DISPLAY_LISTS_SIZE = Agent.getIntSetting("lf.lwjgl-genlists.size", 202800);

    public DisplayListsPatch() {
        super("lwjgl-genlists", "Patches calls to glGenLists to make the lists size reasonable", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        patchPool.addCtTransformer("org.lwjgl.opengl.GL11", new CtTransformer() {
            public void transform(CtClass ctClass) throws Exception {
                // Proper sizes that should have been set for different versions (examples):
                // c0.0.23a_01: 8192
                // c0.25_05_st: 32768
                // in-0130: 196608 <- extreme
                // in-0223: 24576
                // b1.7.3: 16224
                // 1.7.10: 52272

                CtMethod glGenListsMethod = ctClass.getDeclaredMethod("glGenLists");
                glGenListsMethod.insertBefore("" +
                    (Agent.DEBUG ?
                    "System.out.println(\"[LF] glGenLists got called with: \" + $1);" : ""
                    ) +
                    "if ($1 >= 524288) {" +
                    "    $1 = " + DISPLAY_LISTS_SIZE + ";" +
                    "}"
                );
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        if (!super.shouldApply(patchPool)) {
            return false;
        }

        // Only run for Minecraft versions using LWJGL 2
        return patchPool.getRawClass("org.lwjgl.Sys") != null;
    }
}
