package uk.betacraft.legacyfix.patch.impl;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.patch.PatchHelper;

/**
 * Patches LWJGL to play sound in early Indev
 */
public class IndevSoundPatch extends Patch {

    public IndevSoundPatch() {
        super("indevSound", "Fixes sound not playing on early Indev with modern LWJGL versions", true);
    }

    @Override
    public void apply(Instrumentation inst) throws PatchException, Exception {
        CtClass al10Class = pool.getOrNull("org.lwjgl.openal.AL10");

        if (al10Class != null) {
            CtClass byteBufferClass = pool.get("java.nio.ByteBuffer");
            CtMethod alBufferDataMethod = al10Class.getDeclaredMethod("alBufferData", new CtClass[]{PatchHelper.intClass, PatchHelper.intClass, byteBufferClass, PatchHelper.intClass});

            // @formatter:off
            alBufferDataMethod.insertBefore(
                "java.lang.reflect.Field f = java.lang.ClassLoader.getSystemClassLoader().loadClass(\"java.nio.ByteBuffer\").getDeclaredField(\"hb\");" +
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

            inst.redefineClasses(new ClassDefinition[] {new ClassDefinition(Class.forName(al10Class.getName()), al10Class.toBytecode())});
        }
    }
}
