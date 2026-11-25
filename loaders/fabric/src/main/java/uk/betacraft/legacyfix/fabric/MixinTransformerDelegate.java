package uk.betacraft.legacyfix.fabric;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;
import org.spongepowered.asm.transformers.TreeTransformer;

import java.util.List;

public class MixinTransformerDelegate<T extends TreeTransformer & IMixinTransformer> extends TreeTransformer implements IMixinTransformer {
    protected T delegate;

    @Override
    public void audit(MixinEnvironment mixinEnvironment) {
        delegate.audit(mixinEnvironment);
    }

    @Override
    public List<String> reload(String s, ClassNode classNode) {
        return delegate.reload(s, classNode);
    }

    @Override
    public boolean computeFramesForClass(MixinEnvironment mixinEnvironment, String s, ClassNode classNode) {
        return delegate.computeFramesForClass(mixinEnvironment, s, classNode);
    }

    @Override
    public byte[] transformClass(MixinEnvironment mixinEnvironment, String s, byte[] bytes) {
        return delegate.transformClass(mixinEnvironment, s, bytes);
    }

    @Override
    public boolean transformClass(MixinEnvironment mixinEnvironment, String s, ClassNode classNode) {
        return delegate.transformClass(mixinEnvironment, s, classNode);
    }

    @Override
    public byte[] generateClass(MixinEnvironment mixinEnvironment, String s) {
        return delegate.generateClass(mixinEnvironment, s);
    }

    @Override
    public boolean generateClass(MixinEnvironment mixinEnvironment, String s, ClassNode classNode) {
        return delegate.generateClass(mixinEnvironment, s, classNode);
    }

    @Override
    public IExtensionRegistry getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public byte[] transformClassBytes(String s, String s1, byte[] bytes) {
        return delegate.transformClassBytes(s, s1, bytes);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isDelegationExcluded() {
        return delegate.isDelegationExcluded();
    }
}
