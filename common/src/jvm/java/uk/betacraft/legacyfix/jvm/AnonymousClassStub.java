package uk.betacraft.legacyfix.jvm;

import javassist.bytecode.ClassFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

@SuppressWarnings({"unused", "unchecked", "rawtypes"})
public class AnonymousClassStub {
    private static Method defineHiddenClass;
    private static Method lookupClass;
    private static Object nestmateOptions;

    private AnonymousClassStub() {
    }

    public static Class<?> define(Object host, Object bytes, Object cpPatches) {
        Class<?> hostClass = (Class<?>) host;
        byte[] classBytes = (byte[]) bytes;

        if (cpPatches != null && ((Object[]) cpPatches).length > 0) {
            throw new UnsupportedOperationException("Constant pool patches aren't supported");
        }

        try {
            classBytes = moveToPackageOf(hostClass, classBytes);

            if (defineHiddenClass == null) {
                Class<?> lookupType = Class.forName("java.lang.invoke.MethodHandles$Lookup");

                Class<Enum> optionType = (Class<Enum>) Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");

                Object options = Array.newInstance(optionType, 1);
                Array.set(options, 0, Enum.valueOf(optionType, "NESTMATE"));

                lookupClass = lookupType.getMethod("lookupClass");
                defineHiddenClass = lookupType.getMethod("defineHiddenClass", byte[].class, boolean.class, options.getClass());
                nestmateOptions = options;
            }

            Object lookup = ((java.lang.invoke.MethodHandles.Lookup) TrustedLookup.get()).in(hostClass);
            Object hiddenLookup = defineHiddenClass.invoke(lookup, classBytes, Boolean.TRUE, nestmateOptions);
            return (Class<?>) lookupClass.invoke(hiddenLookup);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Couldn't define anonymous class in " + hostClass.getName(), t);
        }
    }

    private static byte[] moveToPackageOf(Class<?> host, byte[] classBytes) throws Exception {
        ClassFile classFile = new ClassFile(new DataInputStream(new ByteArrayInputStream(classBytes)));
        String name = classFile.getName();

        String hostName = host.getName();
        String hostPackage = hostName.lastIndexOf('.') == -1 ? "" : hostName.substring(0, hostName.lastIndexOf('.') + 1);
        String classPackage = name.lastIndexOf('.') == -1 ? "" : name.substring(0, name.lastIndexOf('.') + 1);
        if (hostPackage.equals(classPackage)) {
            return classBytes;
        }

        classFile.renameClass(name, hostPackage + name.substring(classPackage.length()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(out));
        return out.toByteArray();
    }
}
