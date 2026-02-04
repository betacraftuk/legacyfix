package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;

public class IndevSoundPatch extends Patch {
    public IndevSoundPatch() {
        super("indev-sound", "Fixes sound not playing on early Indev with newer LWJGL", true);
    }

    @Override
    public void apply(PatchPool patchPool) throws Exception {
        CtClass al10Class = patchPool.getClass("org.lwjgl.openal.AL10");
        CtClass byteBufferClass = patchPool.getClass("java.nio.ByteBuffer");

        CtMethod alBufferDataMethod = al10Class.getDeclaredMethod("alBufferData", new CtClass[]{Patch.CT_INT, Patch.CT_INT, byteBufferClass, Patch.CT_INT});
        alBufferDataMethod.insertBefore("" +
            "java.lang.reflect.Field f = Thread.currentThread().getContextClassLoader().loadClass(\"java.nio.ByteBuffer\").getDeclaredField(\"hb\");" +
            "f.setAccessible(true);" +
            "byte[] buffer = (byte[]) f.get($3);" +
            "if (buffer != null) {" +
            "   java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(buffer.length);" +
            "   buf.clear();" +
            "   buf.put(buffer);" +
            "   buf.flip();" +
            "   $3 = buf;" +
            "}"
        );

        patchPool.patchClass(al10Class);
    }
}
