package uk.betacraft.legacyfix.util;

import uk.betacraft.legacyfix.LFLogger;

import java.io.ByteArrayInputStream;

public class SkinUtils {
    public static final boolean OVERLAY_OUTER_HEAD_LAYER = System.getProperties().containsKey("lf.OVERLAY_OUTER_HEAD_LAYER");
    public static final boolean OVERLAY_OUTER_BODY_TO_BASE = System.getProperties().containsKey("lf.OVERLAY_OUTER_BODY_TO_BASE");
    public static final boolean ROTATE_BOTTOM_TEXTURES = System.getProperties().containsKey("lf.ROTATE_BOTTOM_TEXTURES");
    public static final boolean CONVERT_ALEX_TO_STEVE = System.getProperties().containsKey("lf.CONVERT_ALEX_TO_STEVE");
    public static final boolean SERVE_AS_64x32 = System.getProperties().containsKey("lf.SERVE_AS_64x32");

    public static byte[] getFixedCape(MinecraftAPIUtils.SkinData skinData) {
        try {
            if (skinData.cape != null) {
                ByteArrayInputStream bis = new ByteArrayInputStream(skinData.cape);

                ImageUtils img = new ImageUtils(bis);
                return img.crop(0, 0, 64, 32).getInByteForm();
            }
        } catch (Throwable t) {
            LFLogger.error("getFixedCape", t);
        }
        return null;
    }

    public static byte[] getFixedSkin(MinecraftAPIUtils.SkinData skinData) {
        try {
            if (skinData.skin != null) {
                ByteArrayInputStream bis = new ByteArrayInputStream(skinData.skin);
                ImageUtils img = new ImageUtils(bis);

                // classic before 0.24
                if (OVERLAY_OUTER_HEAD_LAYER)
                    img = overlayHeadLayer(img);

                // before 1.8
                if (OVERLAY_OUTER_BODY_TO_BASE && img.getImage().getHeight() == 64)
                    img = overlay64to32(img);

                // before 1.8
                if (CONVERT_ALEX_TO_STEVE && skinData.alex)
                    alexToSteve(img);

                // before b1.9-pre1
                if (ROTATE_BOTTOM_TEXTURES)
                    rotateBottomTX(img);

                // before 1.8
                if (SERVE_AS_64x32)
                    img = img.crop(0, 0, 64, 32);

                return img.getInByteForm();
            }
        } catch (Throwable t) {
            LFLogger.error("getFixedSkin", t);
        }
        return null;
    }

    public static void rotateBottomTX(ImageUtils img) {
        // bottom head
        img.setArea(16, 0, img.crop(16, 0, 8, 8).flip(false, true).getImage());
        // bottom head layer
        img.setArea(48, 0, img.crop(48, 0, 8, 8).flip(false, true).getImage());

        // bottom feet
        img.setArea(8, 16, img.crop(8, 16, 4, 4).flip(false, true).getImage());
        // bottom hand
        img.setArea(48, 16, img.crop(48, 16, 4, 4).flip(false, true).getImage());
        // bottom torso
        img.setArea(28, 16, img.crop(28, 16, 8, 4).flip(false, true).getImage());
    }

    public static ImageUtils overlay64to32(ImageUtils img) {
        // 32-64 body
        return img.setArea(0, 16, img.crop(0, 32, 56, 16).getImage(), false);
    }

    public static ImageUtils overlayHeadLayer(ImageUtils img) {
        return img.setArea(0, 0, img.crop(32, 0, 32, 16).getImage(), false);
    }

    public static void alexToSteve(ImageUtils img) {
        // make space for the left arm 1px texture
        img.setArea(48, 20, img.crop(47, 20, 7, 12).getImage());
        // fill the 1px space in between
        img.setArea(47, 20, img.crop(46, 20, 1, 12).getImage());
        // fill the 1px space on the right
        img.setArea(55, 20, img.crop(54, 20, 1, 12).getImage());

        // bottom hand
        img.setArea(48, 16, img.crop(47, 16, 3, 4).getImage());
        // fill the 1px of the shoulder
        img.setArea(47, 16, img.crop(46, 16, 1, 4).getImage());
        // fill the 1px space on the right
        img.setArea(51, 16, img.crop(50, 16, 1, 4).getImage());
    }
}