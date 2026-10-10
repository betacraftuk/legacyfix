package uk.betacraft.legacyfix.patch.impl.thirdparty;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;
import uk.betacraft.legacyfix.patch.api.Patch;
import uk.betacraft.legacyfix.patch.api.PatchPool;
import uk.betacraft.legacyfix.util.OSUtils;
import uk.betacraft.legacyfix.util.launchers.MultiMCUtils;
import uk.betacraft.legacyfix.util.launchers.PrismLauncherUtils;

import javax.swing.*;
import java.awt.*;

public class LauncherPatch extends Patch {
    private boolean multimc = false;
    private boolean prism = false;

    private static final String LINE1 = "LegacyFix has installed into your instance.";
    private static final String LINE2 = "Now you need to restart the game to play!";

    public LauncherPatch() {
        super("launcher", "Patches launchers and their instances to work correctly with LegacyFix", true);
    }

    public void apply(PatchPool patchPool) throws Exception {
        boolean changed;
        if (prism) {
            changed = PrismLauncherUtils.setup();
        } else if (multimc) {
            changed = MultiMCUtils.setup();
        } else {
            return;
        }

        if (changed) {
            Logger.info("launcher", LINE1, LINE2);

            if (OSUtils.getOS() != OSUtils.OS.MACOS || patchPool.getRawClass("org.lwjgl.Version") == null) {
                prompt();
            }

            System.exit(0);
        }
    }

    @Override
    public boolean shouldApply(PatchPool patchPool) {
        return super.shouldApply(patchPool) &&
            ((multimc = MultiMCUtils.detect()) || (prism = PrismLauncherUtils.detect()));
    }

    public static synchronized void prompt() {
        try {
            JPanel description = new JPanel(new GridLayout(2, 0));
            description.add(new JLabel(LINE1));
            description.add(new JLabel(LINE2));

            ImageIcon icon = Agent.ICON;
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.add(description, BorderLayout.NORTH);

            JOptionPane.showOptionDialog(
                null,
                panel,
                "LegacyFix",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                icon,
                new Object[]{"Close"},
                "Close"
            );
        } catch (Throwable t) {
            Logger.error("LauncherPatch", t);
        }
    }
}