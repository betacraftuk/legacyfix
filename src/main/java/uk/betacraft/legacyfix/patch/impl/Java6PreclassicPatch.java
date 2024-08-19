package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;

import javassist.CtClass;
import javassist.bytecode.ClassFile;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;

/**
 * Declares all Pre-Classic classes compliant with Java 5
 */
public class Java6PreclassicPatch extends Patch {

    public Java6PreclassicPatch() {
        super("java6preclassic", "Makes Preclassic playable with Java 5", true);
    }

    @Override
    public void apply(Instrumentation inst) throws PatchException, Exception {
        // order matters
        String[] preclassicClasses = {
                "RubyDung",
                "Textures",
                "Timer",
                "HitResult",
                "Entity",
                "Player",
                "character.Cube",
                "character.Polygon",
                "character.Vec3",
                "character.Vertex",
                "character.Zombie",
                "character.ZombieModel",
                "level.Chunk",
                "level.DirtyChunkSorter",
                "level.Frustum",
                "level.Level",
                "level.LevelListener",
                "level.LevelRenderer",
                "level.PerlinNoiseFilter",
                "level.Tesselator",
                "particle.Particle",
                "particle.ParticleEngine",
                "phys.AABB",
                "level.Tile",
                "level.tile.Tile",
                "level.tile.Bush",
                "level.tile.DirtTile",
                "level.tile.GrassTile"
        };

        String[] packages = {
                "com.mojang.minecraft",
                "com.mojang.rubydung"
        };

        ArrayList<ClassDefinition> defList = new ArrayList<ClassDefinition>();

        // Declare all classes Java 5 compliant
        for (int i = 0; i < packages.length; i++) {
            for (String className : preclassicClasses) {
                CtClass pcClass = pool.getOrNull(packages[i] + "." + className);

                if (pcClass == null)
                    continue;

                ClassFile cf = pcClass.getClassFile();
                cf.setMajorVersion(49);
                cf.setVersionToJava5();

                defList.add(new ClassDefinition(pcClass.toClass(), pcClass.toBytecode()));
            }
        }

        if (!defList.isEmpty())
            inst.redefineClasses(defList.toArray(new ClassDefinition[0]));
    }

    @Override
    public boolean shouldApply() {
        return super.shouldApply() && (pool.getOrNull("com.mojang.minecraft.RubyDung") != null || pool.getOrNull("com.mojang.rubydung.RubyDung") != null);
    }
}
