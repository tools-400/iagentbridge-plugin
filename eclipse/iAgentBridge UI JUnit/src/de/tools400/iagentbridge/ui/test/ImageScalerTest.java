/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.tools400.iagentbridge.ui.tools.ImageScaler;

public class ImageScalerTest {

    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;

    @Test
    public void testScale_halfAverages() {

        int[] pixels = { BLACK, WHITE, 0x204060, 0x406080, //
            BLACK, WHITE, 0x204060, 0x406080 };

        int[] scaled = ImageScaler.scale(pixels, 4, 2, 2, 1);

        assertArrayEquals(new int[] { 0x808080, 0x305070 }, scaled);
    }

    @Test
    public void testScale_partialPixels() {

        // 3 pixels to 2: each target pixel covers 1.5 source pixels
        int[] scaled = ImageScaler.scale(new int[] { 0x000000, 0x0000C0, 0x0000FF }, 3, 1, 2, 1);

        // (0 * 1 + 192 * 0.5) / 1.5 = 64 and (192 * 0.5 + 255 * 1) / 1.5 = 234
        assertEquals(0x000040, scaled[0]);
        assertEquals(0x0000EA, scaled[1]);
    }

    @Test
    public void testScale_keepsThinLine() {

        // A dark line of one pixel in 4 must not vanish when 4 pixels become 3.
        int[] pixels = { WHITE, BLACK, WHITE, WHITE };

        int[] scaled = ImageScaler.scale(pixels, 4, 1, 3, 1);

        // The middle pixel: 2/3 of the black and 2/3 of a white one, 127.5.
        int darkest = 0xFF;
        for (int pixel : scaled) {
            darkest = Math.min(darkest, pixel & 0xFF);
        }
        assertTrue("darkest blue value " + darkest, darkest >= 0x7F && darkest <= 0x80);
    }

    @Test
    public void testScale_sameSize() {

        int[] pixels = { 1, 2, 3, 4 };

        assertArrayEquals(pixels, ImageScaler.scale(pixels, 2, 2, 2, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testScale_enlarge() {
        ImageScaler.scale(new int[] { 1 }, 1, 1, 2, 2);
    }
}
