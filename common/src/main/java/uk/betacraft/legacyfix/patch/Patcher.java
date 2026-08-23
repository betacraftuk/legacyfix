package uk.betacraft.legacyfix.patch;

import javassist.ClassPool;
import javassist.CtClass;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.*;
import uk.betacraft.legacyfix.patch.impl.classic.*;
import uk.betacraft.legacyfix.patch.impl.java.*;
import uk.betacraft.legacyfix.patch.impl.lwjgl.*;
import uk.betacraft.legacyfix.patch.impl.misc.*;
import uk.betacraft.legacyfix.patch.impl.thirdparty.*;

import java.util.*;

public class Patcher implements PatchPool {
    public static final Patch[] BUILT_IN_PATCHES = new Patch[]{
        new ProxyPatch(),
        new LevelProxyPatch(),
        new JavaModulesPatch(),
        new BitDepthPatch(),
        new DisableControllersPatch(),
        new DeAwtPatch(),
        new MousePatch(),
        new VSyncPatch(),
        new GameDirPatch(),
        new IndevSoundPatch(),
        new IntelGraphicsPatch(),
        new ScreenshotPatch(),
        new SeecretSaturdayPatch(),
        new TexturePackFolderPatch(),
        new BetaForgePatch(),
        new ClassicPerformancePatch(),
        new ClassicResizePatch(),
        new DemoPatch()
    };

    public final List<Patch> patches = new ArrayList<Patch>();
    private final ClassPool pool;
    private final List<Transformer> transformers = new ArrayList<Transformer>();
    private final Map<String, List<CtTransformer>> ctTransformers = new HashMap<String, List<CtTransformer>>();

    public Patcher(ClassPool pool) {
        this.pool = pool;
        this.patches.addAll(Arrays.asList(BUILT_IN_PATCHES));
    }

    public void apply() {
        List<String> patchStates = new ArrayList<String>();

        for (Patch patch : this.patches) {
            if (!patch.shouldApply(this)) {
                continue;
            }

            try {
                patch.apply(this);
                patchStates.add(patch.getId() + " - Applied");
            } catch (Throwable e) {
                if (e instanceof PatchUnapplicableException) {
                    patchStates.add(patch.getId() + " - Unapplicable: " + e.getMessage());
                } else if (e instanceof PatchException) {
                    patchStates.add(patch.getId() + " - Error: " + e.getMessage());
                } else {
                    patchStates.add(patch.getId() + " - Exception, see stacktrace");
                    Logger.error(patch, e);
                }

                if (patch.isRequired()) {
                    Logger.error("Patch \"" + patch.getId() + "\" is required, but failed to apply! Exiting.");
                    System.exit(-1);
                }
            }
        }

        if (!patchStates.isEmpty()) {
            Logger.logList("Patches:", patchStates);
        } else {
            Logger.info("No patches applied");
        }
    }

    public CtClass getRawClass(String className) {
        return this.pool.getOrNull(className);
    }

    public void addTransformer(Transformer transformer) {
        this.transformers.add(transformer);
    }

    public void addCtTransformer(String className, CtTransformer transformer) {
        if (!this.ctTransformers.containsKey(className)) {
            this.ctTransformers.put(className, new ArrayList<CtTransformer>());
        }

        this.ctTransformers.get(className).add(transformer);
    }

    public List<Transformer> getTransformers() {
        return this.transformers;
    }

    public Map<String, List<CtTransformer>> getCtTransformers() {
        return ctTransformers;
    }
}
