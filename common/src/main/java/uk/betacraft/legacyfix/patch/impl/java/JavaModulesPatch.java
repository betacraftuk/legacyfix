package uk.betacraft.legacyfix.patch.impl.java;

import sun.misc.Unsafe;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchException;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public class JavaModulesPatch extends Patch {
    public JavaModulesPatch() {
        super("java-modules", "Unlocks internal modules. Required for Java 11+", true, true);
    }

    @Override
    public void apply(PatchPool patchPool) throws PatchException {
        try {
            Class<?> moduleClass = Class.forName("java.lang.Module");
            Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");

            Object unnamedModule = ClassLoader.class.getMethod("getUnnamedModule").invoke(this.getClass().getClassLoader());

            Object bootLayer = moduleLayerClass.getMethod("boot").invoke(null);
            Set<?> modules = (Set<?>) moduleLayerClass.getMethod("modules").invoke(bootLayer);

            Method getPackages = moduleClass.getMethod("getPackages");

            Object handle = implAddExportsOrOpens(moduleClass);
            Method invokeWithArguments = Class.forName("java.lang.invoke.MethodHandle").getMethod("invokeWithArguments", Object[].class);

            for (Object module : modules) {
                Set<?> packages = (Set<?>) getPackages.invoke(module);
                for (Object pkg : packages) {
                    open(invokeWithArguments, handle, module, (String) pkg, unnamedModule);
                }
            }
        } catch (PatchException e) {
            throw e;
        } catch (Exception e) {
            Logger.error(this, e);
            throw new PatchException("Failed to open boot modules");
        }
    }

    private Object implAddExportsOrOpens(Class<?> moduleClass) throws PatchException {
        try {
            Class<?> methodTypeClass = Class.forName("java.lang.invoke.MethodType");
            Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");

            Object methodType = methodTypeClass
                .getMethod("methodType", Class.class, Class[].class)
                .invoke(null, void.class, new Class<?>[] {
                    String.class, moduleClass, boolean.class, boolean.class
                });

            Object lookup = readImplLookup();
            Method findVirtual = lookupClass.getMethod("findVirtual", Class.class, String.class, methodTypeClass);
            return findVirtual.invoke(lookup, moduleClass, "implAddExportsOrOpens", methodType);
        } catch (PatchException e) {
            throw e;
        } catch (Exception e) {
            Logger.error(this, e);
            throw new PatchException("Module.implAddExportsOrOpens is not available");
        }
    }

    private Object readImplLookup() throws PatchException {
        try {
            Class.forName("java.lang.invoke.MethodHandles").getMethod("lookup").invoke(null);

            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe unsafe = (Unsafe) theUnsafe.get(null);

            Class<?> mhLookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");
            Field implLookup = mhLookupClass.getDeclaredField("IMPL_LOOKUP");
            Object base = unsafe.staticFieldBase(implLookup);
            return unsafe.getObject(base, unsafe.staticFieldOffset(implLookup));
        } catch (Exception e) {
            Logger.error(this, e);
            throw new PatchException("Could not obtain IMPL_LOOKUP");
        }
    }

    private void open(Method invokeWithArguments, Object handle, Object module, String pkg, Object unnamedModule) throws PatchException {
        try {
            Object[] args = {module, pkg, unnamedModule, Boolean.TRUE, Boolean.TRUE};
            invokeWithArguments.invoke(handle, new Object[] {args});
        } catch (Exception e) {
            Logger.error(this, e);
            String moduleName;
            try {
                moduleName = (String) module.getClass().getMethod("getName").invoke(module);
            } catch (Exception ignored) {
                moduleName = String.valueOf(module);
            }
            throw new PatchException("Failed to open " + moduleName + "/" + pkg);
        }
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= 11;
    }
}
