package uk.betacraft.legacyfix.fabric;

import javassist.*;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.Transformer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class LFMixinTransformer<T extends TreeTransformer & IMixinTransformer> extends MixinTransformerDelegate<T> {
    private final Patcher patcher;

    @SuppressWarnings({"unchecked", "rawtypes"})
    LFMixinTransformer(T delegate) throws Exception {
        this.delegate = delegate;

        MinecraftGameProvider provider = (MinecraftGameProvider) FabricLoaderImpl.INSTANCE.getGameProvider();
        Field miscGameLibsField = MinecraftGameProvider.class.getDeclaredField("miscGameLibraries");
        miscGameLibsField.setAccessible(true);

        ClassPool pool = new ClassPool(true);
        ClassPool gamePool = new ClassPool(pool);
        List libraries = new ArrayList();
        libraries.add(provider.getGameJar());
        libraries.addAll((List) miscGameLibsField.get(provider));
        for (Object path : libraries) {
            gamePool.appendPathList(path.toString());
            Logger.debug("Loading " + path);
        }
        gamePool.childFirstLookup = true;

        this.patcher = new Patcher(gamePool);
        this.patcher.apply();
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] bytecode) {
        byte[] transformed = this.patcher.getTransformedClass(transformedName);
        if (transformed != null) {
            bytecode = transformed;
        }

        if (bytecode == null) {
            return super.transformClassBytes(name, transformedName, null);
        }

        for (Transformer transformer : this.patcher.getTransformers()) {
            try {
                transformed = transformer.transform(name, bytecode);
                if (transformed != null) {
                    bytecode = transformed;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply transformer on class \"" + name + "\"", e);
            }
        }

        return super.transformClassBytes(name, transformedName, bytecode);
    }
}