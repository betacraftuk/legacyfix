package uk.betacraft.legacyfix.patch.impl.java;

import javassist.CannotCompileException;
import javassist.expr.Cast;
import javassist.expr.ExprEditor;
import javassist.expr.Instanceof;
import javassist.expr.MethodCall;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

public class URLClassLoaderPatch extends Patch {
    private static final String HELPER = "uk.betacraft.legacyfix.jvm.ClassLoaderHelper";
    private static final String URL_CLASS_LOADER = "java.net.URLClassLoader";

    public URLClassLoaderPatch() {
        super("url-classloader", "Mimics Java 8's URLClassLoader for mods that expect it", true);
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!mentions(bytecode, "java/net/URLClassLoader")) {
                    return null;
                }

                byte[] result = instrument(name, bytecode, new ExprEditor() {
                    public void edit(Instanceof i) throws CannotCompileException {
                        if (!isURLClassLoader(i)) {
                            return;
                        }

                        i.replace("$_ = $proceed($1) || ((java.lang.Boolean) " + invokeStatic(HELPER, "isSystemLoader",
                            new String[] {"java.lang.Object.class"}, new String[] {"$1"}) + ").booleanValue();");
                    }

                    public void edit(Cast c) throws CannotCompileException {
                        if (!isURLClassLoader(c)) {
                            return;
                        }

                        c.replace("$_ = (" + URL_CLASS_LOADER + ") " + invokeStatic(HELPER, "asURLClassLoader",
                            new String[] {"java.lang.Object.class"}, new String[] {"$1"}) + ";");
                    }

                    public void edit(MethodCall m) throws CannotCompileException {
                        if (!"java.lang.reflect.Method".equals(m.getClassName()) || !"invoke".equals(m.getMethodName())) {
                            return;
                        }

                        m.replace("" +
                            "if ($0.getDeclaringClass() == " + URL_CLASS_LOADER + ".class" +
                            "    && \"addURL\".equals($0.getName())" +
                            "    && ((java.lang.Boolean) " + invokeStatic(HELPER, "isSystemLoader",
                                new String[] {"java.lang.Object.class"}, new String[] {"$1"}) + ").booleanValue()) {" +
                            "    " + invokeStatic(HELPER, "appendToClassPath",
                                new String[] {"java.net.URL.class"}, new String[] {"(java.net.URL) $2[0]"}) + ";" +
                            "    $_ = null;" +
                            "} else {" +
                            "    $_ = $proceed($$);" +
                            "}"
                        );
                    }
                });

                if (result != null) {
                    Logger.debug("url-classloader", "Redirected URLClassLoader usage in class " + name);
                }
                return result;
            }
        });
    }

    private static boolean isURLClassLoader(Instanceof i) {
        try {
            return URL_CLASS_LOADER.equals(i.getType().getName());
        } catch (javassist.NotFoundException e) {
            return false;
        }
    }

    private static boolean isURLClassLoader(Cast c) {
        try {
            return URL_CLASS_LOADER.equals(c.getType().getName());
        } catch (javassist.NotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 9;
    }
}
