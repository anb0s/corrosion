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
package org.eclipse.corrosion.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.corrosion.wizards.newproject.NewCargoProjectWizard;
import org.eclipse.corrosion.wizards.newproject.NewCargoProjectWizardPage;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.Test;

public class TestNewCargoProjectWizard extends AbstractCorrosionTest {

	private static final String DEFAULT_PROJECT_NAME = "new_rust_project";
	
	@Test
	public void testNewProjectPage() {
		NewCargoProjectWizard wizard = new NewCargoProjectWizard();
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		wizard.init(getWorkbench(), new StructuredSelection());
		dialog.create();
		confirmPageState(wizard, DEFAULT_PROJECT_NAME, "none", true);

		Composite composite = (Composite) wizard.getPages()[0].getControl();
		Button binaryCheckBox = (Button) composite.getChildren()[12];
		binaryCheckBox.setSelection(false);
		confirmPageState(wizard, DEFAULT_PROJECT_NAME, "none", false);

		Button vcsCheckBox = (Button) composite.getChildren()[14];
		vcsCheckBox.setSelection(true);
		confirmPageState(wizard, DEFAULT_PROJECT_NAME, "git", false);

		dialog.close();
	}

	private void confirmPageState(IWizard wizard, String expectedProjectName, String expectedVCS,
			Boolean expectedBinaryState) {
		NewCargoProjectWizardPage page = (NewCargoProjectWizardPage) wizard.getPages()[0];
		assertEquals(expectedProjectName, page.getProjectName());
		assertEquals(expectedVCS, page.getVCS());
		assertEquals(expectedBinaryState, page.isBinaryTemplate());
	}

	@Test
	public void testCreateNewProject() {
		NewCargoProjectWizard wizard = new NewCargoProjectWizard();
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		wizard.init(getWorkbench(), new StructuredSelection());
		dialog.create();

		assertTrue(wizard.canFinish());
		assertTrue(wizard.performFinish());
		dialog.close();
		new DisplayHelper() {

			@Override
			protected boolean condition() {
				return ResourcesPlugin.getWorkspace().getRoot().getProjects().length > 0;
			}
		}.waitForCondition(getShell().getDisplay(), 15000);
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		assertEquals(1, projects.length);
		assertTrue(projects[0].getFile("Cargo.toml").exists());
	}

	@Test
	public void testCreateNewProjectOutOfWorkspace() throws IOException {
		NewCargoProjectWizard wizard = new NewCargoProjectWizard();
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		wizard.init(getWorkbench(), new StructuredSelection());
		dialog.create();
		confirmPageState(wizard, DEFAULT_PROJECT_NAME, "none", true);
		Composite composite = (Composite) wizard.getPages()[0].getControl();
		Optional<Text> locationText = Arrays.stream(composite.getChildren())
				.filter(Text.class::isInstance)
				.map(Text.class::cast)
				.findFirst();
		Path tempDir = Files.createTempDirectory("corrosion-test");
		if (locationText.isPresent()) {
			locationText.get().setText(tempDir.toString());
		} else {
			fail();
		}
		confirmPageState(wizard, tempDir.getFileName().toString(), "none", true);
		assertTrue(wizard.canFinish());
		assertTrue(wizard.performFinish());
		dialog.close();
		new DisplayHelper() {

			@Override
			protected boolean condition() {
				return ResourcesPlugin.getWorkspace().getRoot().getProjects().length > 0;
			}
		}.waitForCondition(getShell().getDisplay(), 15000);
		try {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			assertEquals(1, projects.length);
			IProject project = projects[0];
			assertEquals(tempDir.toFile(), project.getLocation().toFile());
			assertTrue(projects[0].getFile("Cargo.toml").exists());
		} finally {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}

	@Override
	public void tearDown() throws CoreException {
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			try {
				project.delete(true, new NullProgressMonitor());
			} catch (CoreException e) {
				fail(e.getMessage());
			}
		}
		super.tearDown();
	}
}
