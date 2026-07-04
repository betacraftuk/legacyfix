package uk.betacraft.legacyfix.applet;

import java.applet.Applet;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;

public class AppletStub extends Applet implements java.applet.AppletStub {
    private final Applet wrappedApplet;

    public AppletStub(Applet wrapped) {
        this.wrappedApplet = wrapped;
        this.setLayout(new BorderLayout());
        this.add(wrapped, "Center");
    }

    @Override
    public void init() {
        this.wrappedApplet.init();
    }

    @Override
    public void start() {
        this.wrappedApplet.start();
    }

    @Override
    public void destroy() {
        this.wrappedApplet.destroy();
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public URL getDocumentBase() {
        try {
            return new java.net.URL("http://www.minecraft.net/");
        } catch (MalformedURLException ignored) {}

        return null;
    }

    @Override
    public String getParameter(String name) {
        String value = AppletLauncher.getValue(name, null);
        if (value != null) {
            return value;
        }

        try {
            return super.getParameter(name);
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public void resize(int width, int height) {
        this.wrappedApplet.resize(width, height);
    }

    @Override
    public void resize(Dimension d) {
        this.wrappedApplet.resize(d);
    }

    public void appletResize(int width, int height) {
        this.wrappedApplet.resize(width, height);
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        this.wrappedApplet.setVisible(b);
    }
}
