package uk.betacraft.legacyfix.patch.impl;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

/**
 * Patches OpenGlHelper to fix rendering on Intel graphics (when using performance trick JVM argument)
 * <a href="https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1294926-themastercavers-world?page=13#c294">Reference</a>
 */
public class IntelPatch extends Patch {
    public IntelPatch() {
        super("intel", "Patches rendering on Intel", true);
    }

    public void apply(final Instrumentation inst) throws Exception {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className, Class<?> classRedefined, ProtectionDomain domain, byte[] classfileBuffer) {
                CtClass clas = pool.getOrNull(className.replace("/", "."));
                if (clas == null || clas.getName().startsWith("org.lwjgl") || clas.getDeclaredConstructors().length != 1 || clas.isFrozen()) {
                    return null;
                }

                try {
                    final boolean[] openGlHelperMatched = new boolean[1];
                    for (CtMethod glActiveTextureMethod : clas.getDeclaredMethods()) {
                        if (!Modifier.isPublic(glActiveTextureMethod.getModifiers()) ||
                                !Modifier.isStatic(glActiveTextureMethod.getModifiers()) ||
                                !"void".equals(glActiveTextureMethod.getReturnType().getName()) ||
                                glActiveTextureMethod.getParameterTypes().length != 1 ||
                                !"int".equals(glActiveTextureMethod.getParameterTypes()[0].getName())) {
                            continue;
                        }

                        glActiveTextureMethod.instrument(new ExprEditor() {
                            public void edit(MethodCall m) throws CannotCompileException {
                                if ("org.lwjgl.opengl.ARBMultitexture".equals(m.getClassName()) &&
                                        "glActiveTextureARB".equals(m.getMethodName()) &&
                                        "(I)V".equalsIgnoreCase(m.getSignature())) {
                                    openGlHelperMatched[0] = true;
                                    m.replace("{ org.lwjgl.opengl.ARBMultitexture.glClientActiveTextureARB($$); $_ = $proceed($$); }");
                                    LFLogger.debug("intelpatch", "Matched ARBMultitexture.glActiveTextureARB(I)V");
                                } else if ("org.lwjgl.opengl.GL13".equals(m.getClassName()) &&
                                        "glActiveTexture".equals(m.getMethodName()) &&
                                        "(I)V".equalsIgnoreCase(m.getSignature())) {
                                    openGlHelperMatched[0] = true;
                                    m.replace("{ org.lwjgl.opengl.GL13.glClientActiveTexture($$); $_ = $proceed($$); }");
                                    LFLogger.debug("intelpatch", "Matched GL13.glActiveTexture(I)V");
                                }
                            }
                        });

                        if (openGlHelperMatched[0]) {
                            LFLogger.debug("intelpatch", "Found OpenGlHelper and patched it: " + clas.getName());

                            inst.removeTransformer(this); // job is done, don't transform any more classes
                            return clas.toBytecode();
                        }
                    }
                } catch (Throwable t) {
                    LFLogger.error("intelpatch", t);
                }

                return null;
            }
        });
    }
}
