/*********************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen   (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.corrosion.run;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.corrosion.Messages;
import org.eclipse.corrosion.cargo.core.CargoProjectTester;
import org.eclipse.corrosion.cargo.core.CargoTools;
import org.eclipse.corrosion.cargo.ui.OptionSelector;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.model.BaseWorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

public class CargoRunTab extends AbstractLaunchConfigurationTab {

	private Text projectText;
	private Text optionsText;
	private Text argsText;

	private IProject project;

	@Override public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(CargoRunDelegate.PROJECT_ATTRIBUTE, projectText.getText());
		configuration.setAttribute(CargoRunDelegate.RUN_ARGUMENTS_ATTRIBUTE, argsText.getText());
		configuration.setAttribute(CargoRunDelegate.RUN_OPTIONS_ATTRIBUTE, optionsText.getText());
		setDirty(false);
	}

	@Override public void createControl(Composite parent) {
		Composite container = new Group(parent, SWT.BORDER);
		setControl(container);
		GridLayoutFactory.swtDefaults().numColumns(3).applyTo(container);
		container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Label projectLabel = new Label(container, SWT.NONE);
		projectLabel.setText(Messages.CargoRunTab_project);
		projectLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		projectText = new Text(container, SWT.BORDER);
		projectText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		projectText.addModifyListener(e -> {
			setDirty(true);
			if (!projectText.getText().isEmpty()) {
				project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectText.getText());
			} else {
				project = null;
			}
			updateLaunchConfigurationDialog();
		});

		Button browseButton = new Button(container, SWT.NONE);
		browseButton.setText(Messages.CargoRunTab_browse);
		browseButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		browseButton.addSelectionListener(widgetSelectedAdapter(e -> {
			ListSelectionDialog dialog = new ListSelectionDialog(browseButton.getShell(), ResourcesPlugin.getWorkspace().getRoot(), new BaseWorkbenchContentProvider(), new WorkbenchLabelProvider(), Messages.CargoRunTab_selectProject);
			dialog.setTitle(Messages.CargoRunTab_projectSelection);
			int returnCode = dialog.open();
			Object[] results = dialog.getResult();
			if (returnCode == 0 && results.length > 0) {
				projectText.setText(((IProject) results[0]).getName());
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		}));

		createTestCommandGroup(container);
	}

	private void createTestCommandGroup(Composite container) {
		Group testCommandGroup = new Group(container, SWT.NONE);
		testCommandGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1));
		testCommandGroup.setLayout(new GridLayout(4, false));
		testCommandGroup.setText("cargo run [options] [--] [arguments]"); //$NON-NLS-1$

		Label optionLabel = new Label(testCommandGroup, SWT.NONE);
		optionLabel.setText(Messages.CargoRunTab_options);
		optionLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		optionsText = new Text(testCommandGroup, SWT.BORDER);
		optionsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		optionsText.addModifyListener(e -> {
			setDirty(true);
			updateLaunchConfigurationDialog();
		});

		Button optionButton = new Button(testCommandGroup, SWT.NONE);
		optionButton.setText(Messages.CargoRunTab_optionsColon);
		optionButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		optionButton.addSelectionListener(widgetSelectedAdapter(e -> {
			OptionSelector dialog = new OptionSelector(optionButton.getShell(), CargoTools.getOptions("run").stream() //$NON-NLS-1$
					.filter(o -> !o.getFlag().equals("--manifest-path")).collect(Collectors.toList())); //$NON-NLS-1$
			dialog.open();
			String result = dialog.returnOptionSelection();
			if (result != null) {
				optionsText.append(" " + result); //$NON-NLS-1$
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		}));
		createVariablesButton(testCommandGroup, optionsText);

		Label argsLabel = new Label(testCommandGroup, SWT.NONE);
		argsLabel.setText(Messages.CargoRunTab_arguments);
		argsLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		argsText = new Text(testCommandGroup, SWT.BORDER);
		argsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		argsText.addModifyListener(e -> {
			setDirty(true);
			updateLaunchConfigurationDialog();
		});
		createVariablesButton(testCommandGroup, argsText);
	}

	private void createVariablesButton(Composite composite, Text resultText) {
		Button variableButton = new Button(composite, SWT.NONE);
		variableButton.setText(Messages.CargoRunTab_variables);
		variableButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		variableButton.addSelectionListener(widgetSelectedAdapter(e -> {
			StringVariableSelectionDialog variableSelector = new StringVariableSelectionDialog(variableButton.getShell());
			int returnCode = variableSelector.open();
			String result = variableSelector.getVariableExpression();
			if (returnCode == 0 && result != null) {
				resultText.append(result);
			}
			setDirty(true);
			updateLaunchConfigurationDialog();
		}));
	}

	@Override public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(CargoRunDelegate.PROJECT_ATTRIBUTE, ""); //$NON-NLS-1$
		configuration.setAttribute(CargoRunDelegate.RUN_ARGUMENTS_ATTRIBUTE, ""); //$NON-NLS-1$
		configuration.setAttribute(CargoRunDelegate.RUN_OPTIONS_ATTRIBUTE, ""); //$NON-NLS-1$
	}

	@Override public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			projectText.setText(configuration.getAttribute(CargoRunDelegate.PROJECT_ATTRIBUTE, "")); //$NON-NLS-1$
		} catch (CoreException ce) {
			projectText.setText(""); //$NON-NLS-1$
		}
		try {
			optionsText.setText(configuration.getAttribute(CargoRunDelegate.RUN_OPTIONS_ATTRIBUTE, "")); //$NON-NLS-1$
		} catch (CoreException ce) {
			optionsText.setText(""); //$NON-NLS-1$
		}
		try {
			argsText.setText(configuration.getAttribute(CargoRunDelegate.RUN_ARGUMENTS_ATTRIBUTE, "")); //$NON-NLS-1$
		} catch (CoreException ce) {
			argsText.setText(""); //$NON-NLS-1$
		}
	}

	@Override public boolean isValid(ILaunchConfiguration launchConfig) {
		return canSave();
	}

	private static CargoProjectTester tester = new CargoProjectTester();

	@Override public boolean canSave() {
		if (project != null && project.exists() && tester.test(project, CargoProjectTester.PROPERTY_NAME, null, null)) { // $NON-NLS-1$
			setErrorMessage(null);
			return true;
		}
		setErrorMessage(Messages.CargoRunTab_invalidCargoProjectName);
		return false;
	}

	@Override public String getName() {
		return "Main"; //$NON-NLS-1$
	}
}
