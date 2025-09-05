package uk.betacraft.legacyfix.patch.impl;

import com.sun.jna.*;
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import javassist.CtClass;
import javassist.CtMethod;
import uk.betacraft.legacyfix.patch.Patch;
import uk.betacraft.legacyfix.patch.PatchException;
import uk.betacraft.legacyfix.util.OSUtils;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

public class RawInputPatch extends Patch {
    public RawInputPatch() {
        super("rawinput", "Enables raw mouselook on Windows", false);
    }

    @Override
    public void apply(Instrumentation inst) throws Exception {
        if (OSUtils.getOS() != OSUtils.OS.WINDOWS) {
            throw new PatchException("This patch can only be applied on Windows");
        }

        CtClass displayClass = pool.get("org.lwjgl.opengl.Display");
        if (displayClass.isFrozen()) displayClass.defrost();
        CtClass mouseClass = pool.get("org.lwjgl.input.Mouse");
        if (mouseClass.isFrozen()) mouseClass.defrost();

        CtMethod create = displayClass.getDeclaredMethod("create", new CtClass[]{});
        create.insertAfter(
            "{" +
            "  Class rawInputPatch = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.RawInputPatch\");" +
            "  rawInputPatch.getMethod(\"installRawInputHook\", null).invoke(null, null);" +
            "}"
        );

        CtMethod getDX = mouseClass.getDeclaredMethod("getDX", new CtClass[]{});
        getDX.insertBefore(
            "{" +
            "  Class rawInputPatch = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.RawInputPatch\");" +
            "  if (((Boolean) rawInputPatch.getMethod(\"isWndProcHooked\", null).invoke(null, null)).booleanValue())" +
            "    return ((Integer) rawInputPatch.getMethod(\"getRawDX\", null).invoke(null, null)).intValue();" +
            "}"
        );

        CtMethod getDY = mouseClass.getDeclaredMethod("getDY", new CtClass[]{});
        getDY.insertBefore(
            "{" +
            "  Class rawInputPatch = ClassLoader.getSystemClassLoader().loadClass(\"uk.betacraft.legacyfix.patch.impl.RawInputPatch\");" +
            "  if (((Boolean) rawInputPatch.getMethod(\"isWndProcHooked\", null).invoke(null, null)).booleanValue())" +
            "    return ((Integer) rawInputPatch.getMethod(\"getRawDY\", null).invoke(null, null)).intValue();" +
            "}"
        );

        inst.redefineClasses(
            new ClassDefinition(Class.forName(displayClass.getName()), displayClass.toBytecode()),
            new ClassDefinition(Class.forName(mouseClass.getName()), mouseClass.toBytecode())
        );
    }

    public static void installRawInputHook() {
        int nJavaPid = Kernel32.INSTANCE.GetCurrentProcessId();
        HWND hWnd = User32.INSTANCE.FindWindow(null, null);
        while (!hWnd.equals(null)) {
            IntByReference pnWindowPid = new IntByReference(0);
            User32.INSTANCE.GetWindowThreadProcessId(hWnd, pnWindowPid);
            if (nJavaPid == pnWindowPid.getValue()) {
                RAWINPUTDEVICE rid = new RAWINPUTDEVICE();
                rid.usUsagePage = (short) 0x01;
                rid.usUsage = (short) 0x02;
                rid.dwFlags = RIDEV_INPUTSINK;
                rid.hwndTarget = hWnd;

                if (!User32Ex.INSTANCE.RegisterRawInputDevices(new RAWINPUTDEVICE[]{rid}, 1, rid.size()))
                    return;

                originalWndProc = User32.INSTANCE.GetWindowLongPtr(hWnd, GWLP_WNDPROC);
                hookedWndProc = new WindowProc() {
                    public LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                        if (uMsg == WM_INPUT) {
                            WinNT.HANDLE hRawInput = new WinNT.HANDLE(lParam.toPointer());
                            IntByReference pcbSize = new IntByReference(0);
                            User32Ex.INSTANCE.GetRawInputData(hRawInput, RID_INPUT, null, pcbSize, RAWINPUTHEADER.sizeof());

                            if (pcbSize.getValue() > 0) {
                                Memory buffer = new Memory(pcbSize.getValue());
                                if (User32Ex.INSTANCE.GetRawInputData(hRawInput, RID_INPUT, buffer, pcbSize, RAWINPUTHEADER.sizeof()) == pcbSize.getValue()) {
                                    RAWINPUT raw = new RAWINPUT(buffer);
                                    raw.read();
                                    if (raw.header.dwType == RIM_TYPEMOUSE) {
                                        raw.data.setType(RAWINPUT.RAWMOUSE.class);
                                        raw.data.read();
                                        deltaX += raw.data.mouse.lLastX.intValue();
                                        deltaY += raw.data.mouse.lLastY.intValue();
                                    }
                                }
                            }
                        }
                        return User32.INSTANCE.CallWindowProc(new Pointer(originalWndProc.longValue()), hWnd, uMsg, wParam, lParam);
                    }
                };

                User32.INSTANCE.SetWindowLongPtr(hWnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(hookedWndProc));
                break;
            }
            hWnd = User32.INSTANCE.GetWindow(hWnd, GW_HWNDNEXT);
        }
    }

    public static int getRawDX() {
        int dx = deltaX;
        deltaX = 0;
        return dx;
    }

    public static int getRawDY() {
        int dy = deltaY;
        deltaY = 0;
        return -dy;
    }

    public static boolean isWndProcHooked() {
        return hookedWndProc != null;
    }

    private static WindowProc hookedWndProc;
    private static LONG_PTR originalWndProc;
    private static volatile int deltaX;
    private static volatile int deltaY;

    /// Raw input Win32 defs

    public static class RAWINPUTDEVICE extends Structure {
        public short usUsagePage;
        public short usUsage;
        public int dwFlags;
        public HWND hwndTarget;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("usUsagePage", "usUsage", "dwFlags", "hwndTarget");
        }
    }

    public static class RAWINPUTHEADER extends Structure {
        public int dwType;
        public int dwSize;
        public WinNT.HANDLE hDevice;
        public WPARAM wParam;

        public RAWINPUTHEADER() {}

        public RAWINPUTHEADER(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwType", "dwSize", "hDevice", "wParam");
        }

        public static int sizeof() {
            return new RAWINPUTHEADER().size();
        }
    }

    public static class RAWINPUT extends Structure {
        public RAWINPUTHEADER header;
        public RAWINPUTDATA data;

        public RAWINPUT(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("header", "data");
        }

        public static class RAWINPUTDATA extends Union {
            public RAWMOUSE mouse;
        }

        public static class RAWMOUSE extends Structure {
            public USHORT usFlags;
            public BUTTONDATA data;
            public ULONG ulRawButtons;
            public LONG lLastX;
            public LONG lLastY;
            public ULONG ulExtraInformation;

            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("usFlags", "data", "ulRawButtons", "lLastX", "lLastY", "ulExtraInformation");
            }

            public static class BUTTONDATA extends Union {
                public ULONG ulButtons;
                public BUTTONS buttons;

                @Override
                protected List<String> getFieldOrder() {
                    return Arrays.asList("ulButtons", "buttons");
                }
            }

            public static class BUTTONS extends Structure {
                public USHORT usButtonFlags;
                public USHORT usButtonData;

                @Override
                protected List<String> getFieldOrder() {
                    return Arrays.asList("usButtonFlags", "usButtonData");
                }
            }
        }
    }

    public interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean RegisterRawInputDevices(RAWINPUTDEVICE[] pRawInputDevices, int uiNumDevices, int cbSize);
        int GetRawInputData(WinNT.HANDLE hRawInput, int uiCommand, Pointer pData, IntByReference pcbSize, int cbSizeHeader);
    }

    private static final int RID_INPUT = 0x10000003;
    private static final int RIM_TYPEMOUSE = 0x00;
    private static final int WM_INPUT = 0x00FF;
    private static final int RIDEV_INPUTSINK = 0x00000100;
    private static final int GWLP_WNDPROC = -4;
    private static final DWORD GW_HWNDNEXT = new DWORD(2);
}
