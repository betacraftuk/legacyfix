package uk.betacraft.legacyfix.fabric;

import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.game.patch.GameTransformer;

import java.lang.reflect.Field;
import java.util.List;

@SuppressWarnings("unchecked")
public class LFTransformer extends GameTransformer {
    public LFTransformer(GameTransformer transformer, Field patchesField, Field patchedClassesField) throws Exception {
        super(((List<GamePatch>) patchesField.get(transformer)).toArray(new GamePatch[0]));

        patchesField.setAccessible(true);
        patchesField.set(this, patchesField.get(transformer));

        patchedClassesField.setAccessible(true);
        patchedClassesField.set(this, patchedClassesField.get(transformer));

        Field entrypointsField = GameTransformer.class.getDeclaredField("entrypointsLocated");
        entrypointsField.setAccessible(true);
        entrypointsField.set(this, entrypointsField.getBoolean(transformer));
    }

    @Override
    public byte[] transform(String className) {
        byte[] orig = super.transform(className);
        if (orig != null) {
            System.out.println("LF: Transformed class: " + className);
        }

        return orig;
    }
}
