package uk.betacraft.legacyfix.fabric;

import javassist.ClassPool;
import javassist.NotFoundException;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.Patcher;

import java.lang.reflect.Field;
import java.util.List;

public class LFMixinTransformer<T extends TreeTransformer & IMixinTransformer> extends MixinTransformerDelegate<T> {
    private final Patcher patcher;

    @SuppressWarnings("unchecked")
    LFMixinTransformer(T delegate) throws Exception {
        this.delegate = delegate;

        MinecraftGameProvider provider = (MinecraftGameProvider) FabricLoaderImpl.INSTANCE.getGameProvider();
        Field miscGameLibsField = MinecraftGameProvider.class.getDeclaredField("miscGameLibraries");
        miscGameLibsField.setAccessible(true);

        Field gameJarsField = MinecraftGameProvider.class.getDeclaredField("gameJars");
        gameJarsField.setAccessible(true);

        ClassPool pool = new ClassPool(true);
        List libraries = (List) miscGameLibsField.get(provider);
        libraries.addAll((List) gameJarsField.get(provider));
        for (Object path : libraries) {
            try {
                pool.appendPathList(path.toString());
                Logger.debug("Loading " + path);
            } catch (NotFoundException e) {
                throw new RuntimeException("Couldn't append to classpath: " + path, e);
            }
        }

        this.patcher = new Patcher(pool);
        this.patcher.apply();
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] bytecode) {
        byte[] transformed = this.patcher.getTransformedClass(transformedName);
        if (transformed != null) {
            bytecode = transformed;
        }

        return super.transformClassBytes(name, transformedName, bytecode);
    }
}
