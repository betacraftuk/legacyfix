package uk.betacraft.legacyfix.patch.api;

import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.util.JvmUtils;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

public abstract class ClassRedirectPatch extends Patch {
    private final int minJavaVersion;
    private final String[] from, to, dottedFrom, dottedTo;

    protected ClassRedirectPatch(String id, String description, int minJavaVersion, String... redirects) {
        super(id, description, true);
        this.minJavaVersion = minJavaVersion;

        int count = redirects.length / 2;
        this.from = new String[count];
        this.to = new String[count];
        this.dottedFrom = new String[count];
        this.dottedTo = new String[count];
        for (int i = 0; i < count; i++) {
            this.from[i] = redirects[i * 2];
            this.to[i] = redirects[i * 2 + 1];
            this.dottedFrom[i] = this.from[i].replace('/', '.');
            this.dottedTo[i] = this.to[i].replace('/', '.');
        }
    }

    @Override
    public void apply(PatchPool patchPool) {
        patchPool.addTransformer(new Transformer() {
            public byte[] transform(String name, byte[] bytecode) throws Exception {
                if (!mentionsAny(bytecode)) {
                    return null;
                }

                byte[] result = redirect(bytecode);
                if (result != null) {
                    Logger.debug(getId(), "Redirected removed JDK classes in class " + name);
                }
                return result;
            }
        });
    }

    private boolean mentionsAny(byte[] bytecode) {
        for (int i = 0; i < from.length; i++) {
            if (mentions(bytecode, from[i]) || mentions(bytecode, dottedFrom[i])) {
                return true;
            }
        }
        return false;
    }

    private byte[] redirect(byte[] b) throws UnsupportedEncodingException {
        ConstantPool pool = ConstantPool.parse(b);
        if (pool == null) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + 256);
        out.write(b, 0, 10);

        boolean changed = false;
        for (int i = 1; i < pool.count(); i++) {
            if (pool.offset(i) == 0) {
                continue;
            }

            String mapped = pool.tag(i) == ConstantPool.UTF8 ? redirect(pool.utf8(i)) : null;
            if (mapped == null) {
                out.write(b, pool.offset(i), pool.size(i));
                continue;
            }

            byte[] bytes = mapped.getBytes("ISO-8859-1");
            out.write(ConstantPool.UTF8);
            out.write(bytes.length >> 8);
            out.write(bytes.length);
            out.write(bytes, 0, bytes.length);
            changed = true;
        }

        if (!changed) {
            return null;
        }

        out.write(b, pool.end(), b.length - pool.end());
        return out.toByteArray();
    }

    private String redirect(String value) {
        String result = value;
        for (int i = 0; i < from.length; i++) {
            if (value.equals(from[i]) || value.startsWith(from[i] + "$")) {
                return to[i] + value.substring(from[i].length());
            }
            if (value.equals(dottedFrom[i]) || value.startsWith(dottedFrom[i] + "$")) {
                return dottedTo[i] + value.substring(from[i].length());
            }

            result = result
                .replace("L" + from[i] + ";", "L" + to[i] + ";")
                .replace("L" + from[i] + "$", "L" + to[i] + "$")
                .replace("L" + from[i] + "<", "L" + to[i] + "<");
        }
        return result.equals(value) ? null : result;
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) && JvmUtils.getJvmVersion() >= minJavaVersion;
    }
}
