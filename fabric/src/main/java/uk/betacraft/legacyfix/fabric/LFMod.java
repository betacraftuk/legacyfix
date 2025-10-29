package uk.betacraft.legacyfix.fabric;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;

import java.lang.reflect.Field;

@SuppressWarnings("unused")
public class LFMod implements PreLaunchEntrypoint {
    public void onPreLaunch() {
        MinecraftGameProvider provider = (MinecraftGameProvider) FabricLoaderImpl.INSTANCE.getGameProvider();

        try {
            Field transformerField = MinecraftGameProvider.class.getDeclaredField("transformer");
            transformerField.setAccessible(true);

            GameTransformer transformer = (GameTransformer) transformerField.get(provider);
            Field patchesField = GameTransformer.class.getDeclaredField("patches");
            Field patchedClassesField = GameTransformer.class.getDeclaredField("patchedClasses");

            patchesField.setAccessible(true);
            LFTransformer newTransformer = new LFTransformer(transformer, patchesField, patchedClassesField);
            transformerField.set(provider, newTransformer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize LegacyFix transformer", e);
        }
    }
}