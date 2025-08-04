package uk.betacraft.legacyfix;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface INativeBridge extends Library {
    INativeBridge INSTANCE = Native.load("LegacyFixNative", INativeBridge.class);

    // RawInput
    void InstallRawInputHook();
    int GetRawDeltaX();
    int GetRawDeltaY();
    boolean BIsWndProcHooked();
}