package uk.betacraft.legacyfix.proxy;

import uk.betacraft.legacyfix.Agent;
import uk.betacraft.legacyfix.Logger;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;

public class LevelProxyConfig {
    private static final String DEFAULT_SERVER = "https://betacraft.uk";

    private static String configuredServer;

    public static synchronized void promptIfNeeded() {
        if (Agent.hasSetting("lf.proxy.levelServer") || loadConfiguredServer() != null) {
            return;
        }

        String value = "local";
        try {
            final JRadioButton local = new JRadioButton("Local", true);
            final JRadioButton online = new JRadioButton("Online");
            final JTextField server = new JTextField(DEFAULT_SERVER, 24);

            ButtonGroup group = new ButtonGroup();
            group.add(local);
            group.add(online);

            ActionListener listener = new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    server.setEnabled(online.isSelected());
                }
            };
            local.addActionListener(listener);
            online.addActionListener(listener);
            server.setEnabled(false);

            JPanel choices = new JPanel(new GridLayout(0, 1));
            choices.add(local);
            choices.add(online);

            JPanel serverPanel = new JPanel(new BorderLayout(6, 0));
            serverPanel.add(new JLabel("Server:"), BorderLayout.WEST);
            serverPanel.add(server, BorderLayout.CENTER);
            choices.add(serverPanel);

            ImageIcon icon = getIcon();
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.add(getDescription(), BorderLayout.NORTH);
            panel.add(choices, BorderLayout.CENTER);

            JOptionPane.showOptionDialog(
                null,
                panel,
                "LegacyFix",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                icon,
                new Object[]{"Done"},
                "Done"
            );

            if (online.isSelected()) {
                value = normalize(server.getText()) == null ? DEFAULT_SERVER : server.getText().trim();
            }
        } catch (Throwable t) {
            Logger.error("LevelProxyConfig", t);
        }

        saveConfiguredServer(value);
    }

    public static synchronized String getOnlineLevelServer() {
        String explicit = Agent.getSetting("lf.proxy.levelServer", null);
        if (explicit != null) {
            return normalize(explicit);
        }

        return normalize(loadConfiguredServer());
    }

    private static String loadConfiguredServer() {
        if (configuredServer != null) {
            return configuredServer;
        }

        File file = getConfigFile();
        if (!file.exists()) {
            configuredServer = "local";
            return null;
        }

        FileInputStream in = null;
        try {
            byte[] bytes = new byte[(int) file.length()];
            in = new FileInputStream(file);
            int read = in.read(bytes);
            configuredServer = new String(bytes, 0, Math.max(read, 0), "UTF-8").trim();
        } catch (Exception e) {
            Logger.error("LevelProxyConfig", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }

        return configuredServer;
    }

    private static void saveConfiguredServer(String value) {
        configuredServer = value;

        FileOutputStream out = null;
        try {
            File file = getConfigFile();
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }

            out = new FileOutputStream(file);
            out.write(value.getBytes("UTF-8"));
        } catch (Exception e) {
            Logger.error("LevelProxyConfig", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String normalize(String server) {
        if (server == null) {
            return null;
        }

        server = server.trim();
        if (server.length() == 0 || "local".equalsIgnoreCase(server)) {
            return null;
        }

        return server;
    }

    private static JPanel getDescription() {
        JPanel description = new JPanel(new GridLayout(0, 1));
        description.add(new JLabel("What type of level saves would you like to use?"));

        JPanel supporterNote = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        supporterNote.add(new JLabel("Note: Betacraft's online saving requires being a "));

        JLabel link = new JLabel("<html><u>Patron</u></html>");
        link.setForeground(new Color(0x33, 0x66, 0xcc));
        link.setCursor(new Cursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                openUrl("https://patreon.com/Betacraft");
            }
        });

        supporterNote.add(link);
        supporterNote.add(new JLabel("."));
        description.add(supporterNote);

        return description;
    }

    private static void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            Logger.error("LevelProxyConfig", e);
        }
    }

    private static ImageIcon getIcon() {
        URL url = LevelProxyConfig.class.getResource("/assets/legacyfix/icon-outlined.png");
        if (url == null) {
            return null;
        }

        Image image = new ImageIcon(url).getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }

    private static File getConfigFile() {
        return new File(new File(GameArgs.getGameDir(), "legacyfix"), "level-server.txt");
    }
}
