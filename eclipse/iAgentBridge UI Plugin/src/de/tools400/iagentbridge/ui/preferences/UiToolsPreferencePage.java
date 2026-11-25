/*******************************************************************************
 * Copyright (c) 2026 Thomas Raddatz
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *******************************************************************************/

package de.tools400.iagentbridge.ui.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import de.tools400.iagentbridge.ui.Messages;

/**
 * Preference page 'iAgentBridge > UI tools': whether the dialogs, that an
 * action of the UI tools opens in the test instance, are put behind the other
 * windows.
 */
public class UiToolsPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    /**
     * Creates the page.
     */
    public UiToolsPreferencePage() {
        super(GRID);
        setDescription(Messages.Description_preference_page);
    }

    @Override
    public void init(IWorkbench aWorkbench) {
        setPreferenceStore(UiPreferences.getStore());
    }

    @Override
    protected void createFieldEditors() {

        addField(new RadioGroupFieldEditor(UiPreferences.DIALOGS, Messages.Label_Dialogs_of_the_test_instance, 1,
            new String[][] { { Messages.Label_Dialogs_background, UiPreferences.DIALOGS_BACKGROUND },
                { Messages.Label_Dialogs_foreground, UiPreferences.DIALOGS_FOREGROUND } },
            getFieldEditorParent(), true));

        Label note = new Label(getFieldEditorParent(), SWT.WRAP);
        note.setText(Messages.Note_Setting_is_forwarded);
        GridData layout = new GridData(SWT.FILL, SWT.TOP, true, false);
        layout.widthHint = 400;
        note.setLayoutData(layout);
    }
}
