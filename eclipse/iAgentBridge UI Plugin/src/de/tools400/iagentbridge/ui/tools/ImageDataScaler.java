/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;

/**
 * Scales the ImageData of a screenshot down to a width with
 * {@link ImageScaler}. ImageData needs no display, so this runs outside of the
 * UI thread.
 */
public final class ImageDataScaler {

    private ImageDataScaler() {
    }

    /**
     * Scales an image down to a width, keeping its proportions.
     *
     * @param aData - the image
     * @param aMaxWidth - the width, to which a wider image is scaled down
     * @return the scaled image, or the image itself, if it is not wider
     */
    public static ImageData scaleToWidth(ImageData aData, int aMaxWidth) {

        if (aData.width <= aMaxWidth) {
            return aData;
        }

        int newHeight = Math.max(1, (int)Math.round((double)aData.height * aMaxWidth / aData.width));
        int[] scaled = ImageScaler.scale(toRgb(aData), aData.width, aData.height, aMaxWidth, newHeight);

        ImageData result = new ImageData(aMaxWidth, newHeight, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        for (int y = 0; y < newHeight; y++) {
            result.setPixels(0, y, aMaxWidth, scaled, y * aMaxWidth);
        }

        return result;
    }

    private static int[] toRgb(ImageData aData) {

        int[] rgb = new int[aData.width * aData.height];
        int[] row = new int[aData.width];
        PaletteData palette = aData.palette;
        for (int y = 0; y < aData.height; y++) {
            aData.getPixels(0, y, aData.width, row, 0);
            for (int x = 0; x < aData.width; x++) {
                RGB color = palette.getRGB(row[x]);
                rgb[y * aData.width + x] = color.red << 16 | color.green << 8 | color.blue;
            }
        }

        return rgb;
    }
}
