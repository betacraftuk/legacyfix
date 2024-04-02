package uk.betacraft.legacyfix.fix.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.fix.Fix;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class DisableControllersFix extends Fix {
    public DisableControllersFix() {
        super("disableControllers", "Disable controllers");
    }

    @Override
    public boolean apply(Instrumentation inst) {
        try {
            CtClass clazz = pool.get("org.lwjgl.input.Controllers");
            CtMethod method = clazz.getDeclaredMethod("create");
            method.setBody(
                    "{ return; }"
            );

            inst.redefineClasses(new ClassDefinition(Class.forName(clazz.getName()), clazz.toBytecode()));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
