/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.util.Arrays;

/**
 * Scales an image down by averaging the areas, that each new pixel covers. Text
 * stays readable: unlike picking the nearest pixel (ImageData.scaledTo), no
 * line of a character is dropped. Works on RGB values (0xRRGGBB), without SWT
 * types and outside of the UI thread. The rows are scaled first into an array
 * of floats of the new width, then the columns; so a screenshot of 3840 x 2160
 * pixels needs about 33 MB besides its pixels.
 */
public final class ImageScaler {

    private static final int CHANNELS = 3;

    private ImageScaler() {
    }

    /**
     * Scales an image down.
     *
     * @param aPixels - RGB values, row by row
     * @param aWidth - width
     * @param aHeight - height
     * @param aNewWidth - new width, at most the width
     * @param aNewHeight - new height, at most the height
     * @return RGB values of the new image, row by row
     * @throws IllegalArgumentException if the new size is larger
     */
    public static int[] scale(int[] aPixels, int aWidth, int aHeight, int aNewWidth, int aNewHeight) {

        if (aNewWidth > aWidth || aNewHeight > aHeight || aNewWidth < 1 || aNewHeight < 1) {
            throw new IllegalArgumentException("Can only scale down: " + aWidth + "x" + aHeight + " to " + aNewWidth + "x" + aNewHeight); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }

        return scaleColumns(scaleRows(aPixels, aWidth, aHeight, aNewWidth), aNewWidth, aHeight, aNewHeight);
    }

    /**
     * Scales each row from the width to the new width.
     *
     * @return the channels red, green and blue of each pixel, row by row
     */
    private static float[] scaleRows(int[] aPixels, int aWidth, int aHeight, int aNewWidth) {

        float[] result = new float[aNewWidth * aHeight * CHANNELS];
        double step = (double)aWidth / aNewWidth;
        for (int y = 0; y < aHeight; y++) {
            for (int x = 0; x < aNewWidth; x++) {
                double start = x * step;
                double end = start + step;
                double red = 0;
                double green = 0;
                double blue = 0;
                for (int source = (int)start; source < Math.min(aWidth, Math.ceil(end)); source++) {
                    double weight = Math.min(end, source + 1) - Math.max(start, source);
                    int pixel = aPixels[y * aWidth + source];
                    red += (pixel >> 16 & 0xFF) * weight;
                    green += (pixel >> 8 & 0xFF) * weight;
                    blue += (pixel & 0xFF) * weight;
                }
                int index = (y * aNewWidth + x) * CHANNELS;
                result[index] = (float)(red / step);
                result[index + 1] = (float)(green / step);
                result[index + 2] = (float)(blue / step);
            }
        }

        return result;
    }

    /**
     * Scales each column from the height to the new height.
     *
     * @return RGB values, row by row
     */
    private static int[] scaleColumns(float[] aChannels, int aWidth, int aHeight, int aNewHeight) {

        int[] result = new int[aWidth * aNewHeight];
        double[] sums = new double[aWidth * CHANNELS];
        double step = (double)aHeight / aNewHeight;
        for (int y = 0; y < aNewHeight; y++) {
            Arrays.fill(sums, 0);
            double start = y * step;
            double end = start + step;
            for (int source = (int)start; source < Math.min(aHeight, Math.ceil(end)); source++) {
                double weight = Math.min(end, source + 1) - Math.max(start, source);
                int offset = source * aWidth * CHANNELS;
                for (int i = 0; i < sums.length; i++) {
                    sums[i] += aChannels[offset + i] * weight;
                }
            }
            for (int x = 0; x < aWidth; x++) {
                result[y * aWidth + x] = toByte(sums[x * CHANNELS] / step) << 16 | toByte(sums[x * CHANNELS + 1] / step) << 8
                    | toByte(sums[x * CHANNELS + 2] / step);
            }
        }

        return result;
    }

    private static int toByte(double aValue) {
        return (int)Math.max(0, Math.min(255, Math.round(aValue)));
    }
}
