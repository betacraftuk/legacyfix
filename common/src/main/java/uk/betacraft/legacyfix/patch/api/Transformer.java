package uk.betacraft.legacyfix.patch.api;

public interface Transformer {
    byte[] transform(String name, byte[] bytecode) throws Exception;
}
