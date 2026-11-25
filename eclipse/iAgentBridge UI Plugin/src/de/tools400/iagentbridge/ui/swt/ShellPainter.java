/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Draws a shell or a control into an image with Control.print: the control
 * draws itself, so the image is right, also when the window is covered by
 * another one, and the shell is never brought to the front. Control.print on a
 * shell itself returns a white image, so the image of a shell is composed of
 * the images of its children. Title bar and menu bar are no controls of the
 * shell; they are missing. Used in the UI thread only.
 */
public final class ShellPainter {

    private ShellPainter() {
    }

    /**
     * Draws a shell or a control.
     *
     * @param aControl - the shell or the control
     * @return image data
     */
    public static ImageData paint(Control aControl) {

        Display display = aControl.getDisplay();
        if (!(aControl instanceof Shell)) {
            Point size = aControl.getSize();
            return print(display, aControl, Math.max(1, size.x), Math.max(1, size.y));
        }

        Shell shell = (Shell)aControl;
        Rectangle area = shell.getClientArea();
        Image image = new Image(display, Math.max(1, area.width), Math.max(1, area.height));
        try {
            GC gc = new GC(image);
            try {
                gc.setBackground(shell.getBackground());
                gc.fillRectangle(0, 0, area.width, area.height);
                // getChildren() returns the topmost child first: draw from the
                // bottom to the top, so that overlapping children, e.g. a
                // minimized view shown as an overlay, appear as on the screen.
                Control[] children = shell.getChildren();
                for (int i = children.length - 1; i >= 0; i--) {
                    Control child = children[i];
                    if (!child.isVisible()) {
                        continue;
                    }
                    Rectangle bounds = child.getBounds();
                    if (bounds.width <= 0 || bounds.height <= 0) {
                        continue;
                    }
                    Image childImage = new Image(display, print(display, child, bounds.width, bounds.height));
                    try {
                        gc.drawImage(childImage, bounds.x - area.x, bounds.y - area.y);
                    } finally {
                        childImage.dispose();
                    }
                }
            } finally {
                gc.dispose();
            }
            return image.getImageData();
        } finally {
            image.dispose();
        }
    }

    private static ImageData print(Display aDisplay, Control aControl, int aWidth, int aHeight) {

        Image image = new Image(aDisplay, aWidth, aHeight);
        try {
            GC gc = new GC(image);
            try {
                gc.setBackground(aDisplay.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
                gc.fillRectangle(0, 0, aWidth, aHeight);
                aControl.print(gc);
            } finally {
                gc.dispose();
            }
            return image.getImageData();
        } finally {
            image.dispose();
        }
    }
}
