package uk.betacraft.legacyfix.patch;

import javassist.ClassPool;
import javassist.CtClass;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.patch.impl.lwjgl.BitDepthPatch;
import uk.betacraft.legacyfix.patch.impl.lwjgl.DeAwtPatch;
import uk.betacraft.legacyfix.patch.impl.lwjgl.FramePatch;
import uk.betacraft.legacyfix.patch.impl.lwjgl.MousePatch;

import java.util.*;

public class Patcher implements PatchPool {
    public static final Patch[] DEFAULT_PATCHES = new Patch[]{
        new BitDepthPatch(),
        new MousePatch(),
        new DeAwtPatch(),
        new FramePatch()
    };

    public final List<Patch> patches = new ArrayList<Patch>();
    private final ClassPool pool;
    private final Map<String, CtClass> transformedNodes = new HashMap<String, CtClass>();
    private final Map<String, byte[]> transformedClasses = new HashMap<String, byte[]>();
    private final List<Transformer> transformers = new ArrayList<Transformer>();

    public Patcher(ClassPool pool) {
        this.pool = pool;
        this.patches.addAll(Arrays.asList(DEFAULT_PATCHES));
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
                if (e instanceof PatchException) {
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

        for (CtClass node : transformedNodes.values()) {
            String key = node.getName().replace('/', '.');

            try {
                transformedClasses.put(key, node.toBytecode());
            } catch (Exception e) {
                throw new RuntimeException("Error writing transformed class " + key, e);
            }
        }

        int transformed = transformedNodes.size();
        Logger.info(transformed + " class" + (transformed == 1 ? "" : "es") + " transformed");
    }

    public CtClass getClass(String className) {
        CtClass transformedNode = transformedNodes.get(className);
        if (transformedNode != null) {
            return transformedNode;
        }

        return this.pool.getOrNull(className);
    }

    public void patchClass(CtClass patchedClass) {
        transformedNodes.put(patchedClass.getName(), patchedClass);
    }

    public void addTransformer(Transformer transformer) {
        transformers.add(transformer);
    }

    public byte[] getTransformedClass(String name) {
        return transformedClasses.get(name);
    }

    public Map<String, byte[]> getTransformedClasses() {
        return transformedClasses;
    }

    public List<Transformer> getTransformers() {
        return transformers;
    }
}
