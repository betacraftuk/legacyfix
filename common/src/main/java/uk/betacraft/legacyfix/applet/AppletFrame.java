package uk.betacraft.legacyfix.applet;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;

import javax.swing.JFrame;
import java.applet.Applet;
import java.awt.Dimension;

public class AppletFrame extends JFrame {
    private final AppletStub appletStub;

    public AppletFrame(String title, Applet applet) {
        super(title);
        this.appletStub = new AppletStub(applet);
        applet.setStub(this.appletStub);
    }

    public void launch() {
        this.add(this.appletStub);

        try {
            int width = Integer.parseInt(AppletLauncher.getValue("width", "640"));
            int height = Integer.parseInt(AppletLauncher.getValue("height", "480"));
            this.appletStub.setPreferredSize(new Dimension(width, height));
        } catch (Exception e) {
            Logger.error("Failed to parse window size", e);
        }

        this.pack();
        this.setLocationRelativeTo(null);
        this.setResizable(true);
        this.validate();
        this.appletStub.init();
        this.appletStub.start();
        if (Agent.hasSetting("lf.deawt.disable")) {
            this.setVisible(true);
        }
    }
}
