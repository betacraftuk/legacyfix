package uk.betacraft.legacyfix.util;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import uk.betacraft.legacyfix.LFLogger;
import uk.betacraft.legacyfix.LegacyFixAgent;

public class IconUtils {

    static ByteBuffer pixels16 = null;
    static ByteBuffer pixels32 = null;

    public static void loadIcons(String iconPath) throws IOException {
        if (iconPath != null) {
            File iconFile = new File(iconPath);

            if (iconFile.exists() && iconFile.isFile()) {
                pixels32 = getIconForLWJGL(new FileInputStream(iconFile), 32);
                pixels16 = getIconForLWJGL(new FileInputStream(iconFile), 16);
            } else {
                LFLogger.error("No icon found at given path: " + iconPath);

                pixels16 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 16);
                pixels32 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 32);
            }
        } else {
            pixels16 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 16);
            pixels32 = getIconForLWJGL(LegacyFixAgent.class.getResourceAsStream("/favicon.png"), 32);
        }
    }

    private static ByteBuffer getIconForLWJGL(InputStream stream, int resolution) throws IOException {
        final Image read = ImageIO.read(stream).getScaledInstance(resolution, resolution, Image.SCALE_SMOOTH);

        BufferedImage bufImg = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);

        Graphics g = bufImg.getGraphics();
        g.drawImage(read, 0, 0, null);
        g.dispose();

        final int[] rgb = bufImg.getRGB(0, 0, resolution, resolution, null, 0, resolution);
        final ByteBuffer allocate = ByteBuffer.allocate(4 * rgb.length);

        for (final int n : rgb) {
            allocate.putInt(n << 8 | (n >> 24 & 0xFF));
        }

        allocate.flip();
        return allocate;
    }
}
