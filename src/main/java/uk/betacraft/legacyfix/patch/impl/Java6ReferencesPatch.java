package uk.betacraft.legacyfix.patch.impl;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.lang.instrument.Instrumentation;

/**
 * Makes certain versions compatible with Java 5, as they were supposed to be
 */
public class Java6ReferencesPatch extends Patch {
    public Java6ReferencesPatch() {
        super("java6-refs", "Makes versions c0.0.15a to c0.0.16a_02 and b1.3 playable with Java 5", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass affectedClassicClass = pool.getOrNull("com.mojang.a.a");

        if (affectedClassicClass != null) {
            affectedClassicClass.instrument(new ExprEditor() {
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getSignature().equals("(Ljava/nio/charset/Charset;)[B")) {
                        m.replace("$_ = $0.getBytes(\"UTF-8\");");
                    }
                }

                public void edit(NewExpr m) throws CannotCompileException {
                    try {
                        m.getConstructor();
                    } catch (NotFoundException e) {
                        m.replace("$_ = new java.lang.String($1, \"UTF-8\");");
                    }
                }
            });

            this.redefineClass(inst, affectedClassicClass);
        }

        String[] affectedBetaClassNames = new String[]{"dz", "fp", "jn", "nr"};
        for (String className : affectedBetaClassNames) {
            CtClass affectedBetaClass = pool.getOrNull(className);
            if (affectedBetaClass == null) {
                continue;
            }

            LFLogger.debug("java6-refs", "Processing: " + affectedBetaClass.getName());

            affectedBetaClass.instrument(new ExprEditor() {

                public void edit(MethodCall m) throws CannotCompileException {
                    if ("java.lang.String".equals(m.getClassName()) &&
                        "isEmpty".equals(m.getMethodName()) &&
                        "()V".equalsIgnoreCase(m.getSignature())) {

                        m.replace("$_ = $0.length() == 0;");
                    }
                }
            });

            this.redefineClass(inst, affectedBetaClass);
            LFLogger.debug("java6-refs", "Finished processing: " + affectedBetaClass.getName());
        }
    }

    @Override
    public boolean shouldApply() {
        return super.shouldApply() && JvmUtils.getJvmVersion() < 6;
    }
}
