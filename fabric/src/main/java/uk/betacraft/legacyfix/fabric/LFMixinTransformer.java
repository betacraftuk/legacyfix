package uk.betacraft.legacyfix.fabric;

import javassist.*;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.fabricmc.loader.impl.util.Arguments;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.proxy.GameArgs;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.CtTransformer;
import uk.betacraft.legacyfix.patch.api.Transformer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class LFMixinTransformer<T extends TreeTransformer & IMixinTransformer> extends MixinTransformerDelegate<T> {
    private final Patcher patcher;
    private final ClassPool mainPool;

    @SuppressWarnings({"unchecked", "rawtypes"})
    LFMixinTransformer(T delegate) throws Exception {
        this.delegate = delegate;

        MinecraftGameProvider provider = (MinecraftGameProvider) FabricLoaderImpl.INSTANCE.getGameProvider();
        Field miscGameLibsField = MinecraftGameProvider.class.getDeclaredField("miscGameLibraries");
        miscGameLibsField.setAccessible(true);

        ClassPool pool = new ClassPool(true);
        this.mainPool = new ClassPool(pool);
        List libraries = new ArrayList();
        libraries.add(provider.getGameJar());
        libraries.addAll((List) miscGameLibsField.get(provider));
        for (Object path : libraries) {
            this.mainPool.appendPathList(path.toString());
            Logger.debug("Loading " + path);
        }
        this.mainPool.childFirstLookup = true;

        Arguments args = provider.getArguments();
        GameArgs.setArgs(args.get("username"), args.get("session"));

        this.patcher = new Patcher(this.mainPool);
        this.patcher.apply();
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] bytecode) {
        if (bytecode == null) {
            return super.transformClassBytes(name, transformedName, null);
        }

        List<CtTransformer> ctTransformers = this.patcher.getCtTransformers().get(transformedName);
        if (ctTransformers != null && !ctTransformers.isEmpty()) {
            try {
                ClassPool ctPool = new ClassPool(this.mainPool);
                ctPool.childFirstLookup = true;
                ctPool.insertClassPath(new ByteArrayClassPath(transformedName, bytecode));

                CtClass ctClass = ctPool.get(transformedName);
                for (CtTransformer ctTransformer : ctTransformers) {
                    ctTransformer.transform(ctClass);
                }

                if (ctClass.isModified()) {
                    bytecode = ctClass.toBytecode();
                }

                ctClass.detach();
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply CtTransformer on class \"" + name + "\"", e);
            }
        }

        for (Transformer transformer : this.patcher.getTransformers()) {
            try {
                byte[] transformed = transformer.transform(name, bytecode);
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