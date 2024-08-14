package uk.betacraft.legacyfix.patch.impl;

import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

public class DisableControllersPatch extends Patch {
    public DisableControllersPatch() {
        super("disableControllers", "Disables controller support", true);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        CtClass clazz = pool.get("org.lwjgl.input.Controllers");
        CtMethod method = clazz.getDeclaredMethod("create");
        method.setBody(
                "{ return; }"
        );

        inst.redefineClasses(new ClassDefinition(Class.forName(clazz.getName()), clazz.toBytecode()));
    }
}
