package uk.betacraft.legacyfix;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class VisualizerLauncher {
    private static String canvasStartMethodName = null;
    private static String canvasRunnerClassName = null;

    public static boolean launchPreviewApplet(String className) {
        try {
            Class<?> previewAppletJavaClass = ClassLoader.getSystemClassLoader().loadClass(className);

            Field canvasField = previewAppletJavaClass.getDeclaredFields()[0];
            final Class<?> canvasJavaClass = canvasField.getType();

            CtClass previewAppletClass = ClassPool.getDefault().get(previewAppletJavaClass.getName());

            if (previewAppletClass.isFrozen()) {
                previewAppletClass.defrost();
            }

            CtMethod startMethod = previewAppletClass.getDeclaredMethod("start");
            startMethod.instrument(new ExprEditor() {
                public void edit(MethodCall m) {
                    if (canvasJavaClass.getName().equals(m.getClassName())) {
                        canvasStartMethodName = m.getMethodName();
                    } else if ("start".equals(m.getMethodName()) && canvasRunnerClassName == null) {
                        canvasRunnerClassName = m.getClassName();
                    }
                }
            });

            Class<?> canvasRunnerClass = null;
            Method canvasStartMethod = null;

            if (canvasRunnerClassName != null) {
                canvasRunnerClass = Class.forName(canvasRunnerClassName);
            } else if (canvasStartMethodName != null) {
                canvasStartMethod = canvasJavaClass.getDeclaredMethod(canvasStartMethodName);
            } else {
                Logger.error("Could not find IsomPreviewCanvas.start method");
                return false;
            }

            final Canvas previewCanvasInstance = (Canvas) canvasJavaClass.newInstance();

            Frame frame = new Frame("Minecraft Infinite Map Visualizer");
            frame.setLayout(new BorderLayout());
            frame.add(previewCanvasInstance, "Center");
            previewCanvasInstance.setPreferredSize(new Dimension(LegacyFixLauncher.getWidth(), LegacyFixLauncher.getHeight()));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent windowevent) {
                    System.exit(0);
                }
            });

            if (canvasStartMethod != null) {
                canvasStartMethod.invoke(previewCanvasInstance);
            } else {
                Constructor<?> canvasRunnerConstructor = canvasRunnerClass.getDeclaredConstructor(canvasJavaClass);
                canvasRunnerConstructor.setAccessible(true);

                Thread canvasRunnerInstance = (Thread) canvasRunnerConstructor.newInstance(canvasJavaClass.cast(previewCanvasInstance));
                canvasRunnerInstance.start();

                Thread canvasThread = new Thread((Runnable) previewCanvasInstance);
                canvasThread.start();
            }

            return true;
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            Logger.error("Failed attempt to run infinite map visualizer! Tried \"" + className + "\"");
            Logger.error("launchPreviewApplet", t);
        }

        return false;
    }
}
