/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tools400.iagentbridge.mcp.McpToolResult;
import de.tools400.iagentbridge.mcp.ToolArguments;
import de.tools400.iagentbridge.ui.model.SnapshotWriter;
import de.tools400.iagentbridge.ui.swt.ShellPainter;
import de.tools400.iagentbridge.ui.swt.ShellPicker;
import de.tools400.iagentbridge.ui.swt.UiThread;
import de.tools400.iagentbridge.ui.swt.UiThreadBusyException;
import de.tools400.iagentbridge.ui.swt.WidgetModelBuilder;
import de.tools400.iagentbridge.ui.swt.WidgetRefs;

/**
 * Tool 'ui_screenshot': an image of a shell or a control of the test instance,
 * drawn by the controls themselves, so that it is right, also when the window
 * is covered, and the test instance is never brought to the front.
 */
public class UiScreenshotTool extends AbstractUiTool {

    private static final String DESCRIPTION = "Returns a screenshot of a shell or a control of the test instance of RDi as PNG image " //$NON-NLS-1$
        + "and saves it in a file. The controls draw themselves, so the image is right, also when other windows cover the test instance; " //$NON-NLS-1$
        + "the test instance is not brought to the front. The image of a shell has no title bar and no menu bar." //$NON-NLS-1$
        + DESCRIPTION_SUFFIX;

    private static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
        + "\"shell\":{\"type\":\"string\",\"description\":\"Reference or title of a shell. Default: the active shell.\"}," //$NON-NLS-1$
        + "\"ref\":{\"type\":\"string\",\"description\":\"Reference of a control, e.g. w17, instead of a shell.\"}," //$NON-NLS-1$
        + "\"maxWidth\":{\"type\":\"integer\",\"description\":\"Wider images are scaled down to this width. Default: 1280.\"}}}"; //$NON-NLS-1$

    private static final int DEFAULT_MAX_WIDTH = 1280;
    private static final int MIN_WIDTH = 100;
    private static final String MIME_PNG = "image/png"; //$NON-NLS-1$

    @Override
    public String getName() {
        return "ui_screenshot"; //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        return new JsonParser().parse(INPUT_SCHEMA).getAsJsonObject();
    }

    @Override
    protected McpToolResult executeInTestInstance(JsonObject anArguments) throws Exception {

        String shell = UiArguments.getOptionalString(anArguments, "shell"); //$NON-NLS-1$
        String ref = UiArguments.getOptionalString(anArguments, "ref"); //$NON-NLS-1$
        UiArguments.checkShellOrRef(shell, ref);
        int maxWidth = ToolArguments.getInt(anArguments, "maxWidth", DEFAULT_MAX_WIDTH, MIN_WIDTH); //$NON-NLS-1$

        Display display = getDisplay();
        Capture capture;
        try {
            capture = UiThread.read(display, UI_TIMEOUT_SECONDS, () -> capture(display, shell, ref));
        } catch (UiThreadBusyException e) {
            return busy(e);
        }

        // Scale and encode outside of the UI thread: ImageData and ImageLoader
        // need no display.
        ImageData data = ImageDataScaler.scaleToWidth(capture.data, maxWidth);
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { data };
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        loader.save(png, SWT.IMAGE_PNG);
        byte[] bytes = png.toByteArray();

        File file = ScreenshotFiles.save(ScreenshotFiles.getDirectory(), bytes, new Date());

        String text = "Screenshot of " + capture.description + ", " + data.width + "x" + data.height + " pixels" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            + (capture.data.width > maxWidth ? " (scaled down from " + capture.data.width + "x" + capture.data.height + ")" : "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            + ", saved in " + file.getPath(); //$NON-NLS-1$

        return McpToolResult.success(text).withImage(bytes, MIME_PNG);
    }

    /**
     * Draws the shell or the control in the UI thread.
     */
    private static Capture capture(Display aDisplay, String aShell, String aRef) {

        Control control;
        if (aRef != null) {
            Widget widget = WidgetRefs.get(aRef);
            if (!(widget instanceof Control)) {
                throw new IllegalArgumentException("Ref " + aRef + " is an item, not a control. Use the reference of its tree, table or folder."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            control = (Control)widget;
            if (!control.isVisible()) {
                throw new IllegalArgumentException("Ref " + aRef + " is not visible."); //$NON-NLS-1$ //$NON-NLS-2$
            }
        } else if (aShell != null) {
            control = ShellPicker.find(aDisplay, aShell);
        } else {
            control = ShellPicker.activeShell(aDisplay);
            if (control == null) {
                throw new IllegalArgumentException("No shell of the test instance is visible."); //$NON-NLS-1$
            }
        }

        String description;
        if (control instanceof Shell) {
            description = "shell " + SnapshotWriter.quote(((Shell)control).getText()) + " [" + WidgetRefs.of(control) + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } else {
            description = WidgetModelBuilder.typeName(control) + " [" + WidgetRefs.of(control) + "]"; //$NON-NLS-1$ //$NON-NLS-2$
        }

        return new Capture(ShellPainter.paint(control), description);
    }

    /**
     * The image of a shell or a control.
     */
    private static final class Capture {

        private final ImageData data;
        private final String description;

        private Capture(ImageData aData, String aDescription) {
            data = aData;
            description = aDescription;
        }
    }
}
