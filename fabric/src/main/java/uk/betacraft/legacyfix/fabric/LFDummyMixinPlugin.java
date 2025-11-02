package uk.betacraft.legacyfix.fabric;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;
import uk.betacraft.legacyfix.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public class LFDummyMixinPlugin implements IMixinConfigPlugin {
    static {
        Logger.info("Hooking Mixin transformer");
        try {
            ClassLoader knotClassLoader = LFDummyMixinPlugin.class.getClassLoader(); // net.fabricmc.loader.impl.launch.knot.KnotClassLoader
            Field delegateField = knotClassLoader.getClass().getDeclaredField("delegate");
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(knotClassLoader); // net.fabricmc.loader.impl.launch.knot.KnotClassDelegate
            Field mixinTransformerField = delegate.getClass().getDeclaredField("mixinTransformer");
            mixinTransformerField.setAccessible(true);
            mixinTransformerField.set(delegate, new LFMixinTransformer((TreeTransformer) mixinTransformerField.get(delegate)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean shouldApplyMixin(String s, String s1) {
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void onLoad(String s) {
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {
    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {
    }
}
