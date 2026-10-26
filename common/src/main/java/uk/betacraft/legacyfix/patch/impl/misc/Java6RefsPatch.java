package uk.betacraft.legacyfix.patch.impl.misc;

import javassist.*;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.Patcher;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.patch.api.Transformer;
import uk.betacraft.legacyfix.util.JvmUtils;

public class Java6RefsPatch extends Patch {
    public Java6RefsPatch() {
        super("java6-refs", "Patches uses of Java 6 APIs in vanilla Minecraft versions that should be compliant with Java 5", true);
    }

    private static boolean modified = false;
    @Override
    public void apply(final PatchPool patchPool) throws Exception {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (name == null) {
                    CtClass clas = ctFromBytes(bytecode);
                    if (clas == null) {
                        return null;
                    }
                    name = clas.getName();
                }

                for (String ignore : Patcher.JVM_CLASS_IGNORES) {
                    if (name.startsWith(ignore)) {
                        return null;
                    }
                }

                try {
                    ClassPool ctPool = new ClassPool(true);
                    ctPool.appendClassPath(new ByteArrayClassPath(name, bytecode));
                    CtClass clas = ctPool.get(name);

                    final String finalName = name;
                    clas.instrument(new ExprEditor() {
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (!"java.lang.String".equals(m.getClassName())) {
                                return;
                            }

                            if ("getBytes".equals(m.getMethodName()) &&
                                "(Ljava/nio/charset/Charset;)[B".equals(m.getSignature())) {

                                m.replace("$_ = $0.getBytes(\"UTF-8\");");
                                Logger.debug("java6-refs", "Replaced String.getBytes(Charset) in class " + finalName);
                                modified = true;
                            } else if ("isEmpty".equals(m.getMethodName()) &&
                                "()Z".equalsIgnoreCase(m.getSignature())) {

                                m.replace("$_ = $0.length() == 0;");
                                Logger.debug("java6-refs", "Replaced String.isEmpty() in class " + finalName);
                                modified = true;
                            }
                        }

                        public void edit(NewExpr m) throws CannotCompileException {
                            if (!"java.lang.String".equals(m.getClassName()) ||
                                    !"([BLjava/nio/charset/Charset;)V".equals(m.getSignature())) {
                                return;
                            }

                            try {
                                m.getConstructor();
                            } catch (NotFoundException e) {
                                m.replace("$_ = new java.lang.String($1, \"UTF-8\");");
                                Logger.debug("java6-refs", "Replaced new String(byte[],Charset) in class " + finalName);
                                modified = true;
                            }
                        }
                    });

                    if (modified) {
                        return clas.toBytecode();
                    }
                } catch (Throwable t) {
                    Logger.error("java6-refs", t);
                }

                return null;
            }
        });
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() < 6;
    }
}
