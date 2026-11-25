package java.applet;

import java.awt.Panel;
import java.net.URL;

public class Applet extends Panel {
    private transient AppletStub stub;

    public final void setStub(AppletStub stub) {
        this.stub = stub;
    }

    public boolean isActive() {
        return stub != null && stub.isActive();
    }

    public URL getDocumentBase() {
        return stub.getDocumentBase();
    }

    public URL getCodeBase() {
        return stub.getCodeBase();
    }

    public String getParameter(String name) {
        return stub.getParameter(name);
    }

    public AppletContext getAppletContext() {
        return stub.getAppletContext();
    }

    public void init() {
    }

    public void start() {
    }

    public void stop() {
    }

    public void destroy() {
    }
}
