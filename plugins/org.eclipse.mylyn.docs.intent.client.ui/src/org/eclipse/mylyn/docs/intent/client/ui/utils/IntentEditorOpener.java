/*******************************************************************************
 * Copyright (c) 2010, 2011 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.mylyn.docs.intent.client.ui.utils;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.mylyn.docs.intent.client.ui.IntentEditorActivator;
import org.eclipse.mylyn.docs.intent.client.ui.editor.IntentDocumentProvider;
import org.eclipse.mylyn.docs.intent.client.ui.editor.IntentEditor;
import org.eclipse.mylyn.docs.intent.client.ui.editor.IntentEditorInput;
import org.eclipse.mylyn.docs.intent.client.ui.logger.IntentUiLogger;
import org.eclipse.mylyn.docs.intent.client.ui.repositoryconnection.EditorElementListAdapter;
import org.eclipse.mylyn.docs.intent.collab.handlers.RepositoryObjectHandler;
import org.eclipse.mylyn.docs.intent.collab.handlers.adapters.RepositoryAdapter;
import org.eclipse.mylyn.docs.intent.collab.handlers.impl.ReadOnlyRepositoryObjectHandlerImpl;
import org.eclipse.mylyn.docs.intent.collab.handlers.impl.ReadWriteRepositoryObjectHandlerImpl;
import org.eclipse.mylyn.docs.intent.collab.handlers.impl.notification.elementList.ElementListAdapter;
import org.eclipse.mylyn.docs.intent.collab.handlers.impl.notification.elementList.ElementListNotificator;
import org.eclipse.mylyn.docs.intent.collab.handlers.notification.Notificator;
import org.eclipse.mylyn.docs.intent.collab.repository.Repository;
import org.eclipse.mylyn.docs.intent.collab.repository.RepositoryConnectionException;
import org.eclipse.mylyn.docs.intent.collab.utils.RepositoryCreatorHolder;
import org.eclipse.mylyn.docs.intent.core.document.IntentGenericElement;
import org.eclipse.mylyn.docs.intent.core.query.IntentHelper;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * Class used for opening ReStrucutred Models editor.
 * 
 * @author <a href="mailto:alex.lagarde@obeo.fr">Alex Lagarde</a>
 * @author <a href="mailto:william.piers@obeo.fr">William Piers</a>
 */
public final class IntentEditorOpener {

	/**
	 * EditorUtil constructor.
	 */
	private IntentEditorOpener() {

	}

	/**
	 * Open an editor on the element with the given identifier.
	 * 
	 * @param repository
	 *            The repository to use for this editor
	 * @param identifier
	 *            the identifier of the element.
	 * @param readOnlyMode
	 *            indicates if the editor should be opened in readOnly mode.
	 * @param forceNewEditor
	 *            if true, will open in a new editor anyway. If false, will open in a new editor or select
	 *            inside of an already opened editor
	 * @return the opened editor
	 * @throws PartInitException
	 *             if the editor cannot be opened.
	 */
	public static IntentEditor openIntentEditor(Repository repository, Object identifier, boolean readOnlyMode,
			boolean forceNewEditor) throws PartInitException {
		return openIntentEditor(repository, identifier, readOnlyMode, null, forceNewEditor);
	}

	/**
	 * Open an editor on the element with the given identifier.
	 * 
	 * @param repository
	 *            The repository to use for this editor
	 * @param identifier
	 *            the identifier of the element.
	 * @param readOnlyMode
	 *            indicates if the editor should be opened in readOnly mode.
	 * @param elementToSelectRangeWith
	 *            the element on which the created editor should select its range (can be null).
	 * @param forceNewEditor
	 *            if true, will open in a new editor anyway. If false, will open in a new editor or select
	 *            inside of an already opened editor
	 * @return the opened editor
	 * @throws PartInitException
	 *             if the editor cannot be opened.
	 */
	public static IntentEditor openIntentEditor(Repository repository, Object identifier, boolean readOnlyMode,
			EObject elementToSelectRangeWith, boolean forceNewEditor) throws PartInitException {
		IntentEditor openedEditor = null;
		IStatus status = null;

		try {
			// We get the element on which open this editor

			RepositoryAdapter repositoryAdapter = RepositoryCreatorHolder.getCreator()
					.createRepositoryAdapterForRepository(repository);
			repositoryAdapter.setSendSessionWarningBeforeSaving(false);

			if (readOnlyMode) {
				repositoryAdapter.openReadOnlyContext();
			} else {
				repositoryAdapter.openSaveContext();
			}

			EObject elementToOpen = null;
			elementToOpen = repositoryAdapter.getElementWithID(identifier);

			if (!forceNewEditor) {
				// Step 2 : if an editor containing this element is already opened
				IntentEditor editor = getAlreadyOpenedEditor(elementToOpen);
				if (editor != null) {
					editor.getEditorSite().getPage().activate(editor);
					openedEditor = editor;
					editor.selectRange((IntentGenericElement)elementToOpen);
				}
			}

			if (openedEditor == null) {

				// Step 3 : creation of the Handler in the correct mode
				RepositoryObjectHandler elementHandler = null;

				if (readOnlyMode) {
					elementHandler = new ReadOnlyRepositoryObjectHandlerImpl();
					elementHandler.setRepositoryAdapter(repositoryAdapter);
				} else {
					elementHandler = new ReadWriteRepositoryObjectHandlerImpl(repositoryAdapter);
				}

				// Step 4 : creation of a Notificator listening changes on this element and compilation
				// errors.
				Set<EObject> listenedObjects = new HashSet<EObject>();
				listenedObjects.add(elementToOpen);
				ElementListAdapter adapter = new EditorElementListAdapter();
				Notificator listenedElementsNotificator = new ElementListNotificator(listenedObjects, adapter);
				elementHandler.setNotificator(listenedElementsNotificator);

				// Step 5 : we open a new editor.
				IWorkbenchPage page = null;
				try {
					page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					openedEditor = IntentEditorOpener.openEditor(page, elementToOpen, repository,
							elementHandler);

				} catch (NullPointerException e) {
					status = new Status(IStatus.ERROR, IntentEditorActivator.PLUGIN_ID,
							"An unexpected error has occured");
					throw new PartInitException(status);
				}
			}

			return openedEditor;
		} catch (RepositoryConnectionException e) {
			status = new Status(IStatus.ERROR, IntentEditorActivator.PLUGIN_ID,
					"Editor cannot be openned : can't connect to the repository ");
			throw new PartInitException(status);
		}
	}

	/**
	 * If an editor is already opened on the given element, returns it ; return null otherwise.
	 * 
	 * @param elementToOpen
	 *            the element to search in editors
	 * @return an IntentEditor already opened on the given element, null otherwise.
	 */
	public static IntentEditor getAlreadyOpenedEditor(EObject elementToOpen) {
		IntentEditor alreadyOpenedEditor = null;
		IWorkbenchPage activePage = null;
		IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (activeWorkbenchWindow != null) {
			activePage = activeWorkbenchWindow.getActivePage();
		}

		if (activePage != null) {

			// While no editor containing the given element has been found
			IEditorReference[] editorReferences = activePage.getEditorReferences();
			int editorCount = 0;
			while ((editorCount < editorReferences.length) && alreadyOpenedEditor == null) {
				IEditorReference editorRefrence = editorReferences[editorCount];
				IEditorPart editor = editorRefrence.getEditor(false);

				if (editor instanceof IntentEditor) {
					if (((IntentEditor)editor).containsElement((IntentGenericElement)elementToOpen)) {
						alreadyOpenedEditor = (IntentEditor)editor;
						activePage.activate(alreadyOpenedEditor);
					}
				}
				editorCount++;
			}
		}

		return alreadyOpenedEditor;
	}

	/**
	 * Open an editor on the given IntentModel element.
	 * 
	 * @param page
	 *            the page in which the editor should be opened
	 * @param intentElementToOpen
	 *            the Intent element to open
	 * @param handler
	 *            Handler that will notify the editor of any change occurred on the listened elements
	 * @param repository
	 *            The interface that represents the connection to the repository
	 * @return the opened editor
	 * @throws PartInitException
	 *             if the editor cannot be opened.
	 */
	private static IntentEditor openEditor(IWorkbenchPage page, Object intentElementToOpen, Repository repository,
			RepositoryObjectHandler handler) throws PartInitException {

		// If we can't open a IntentEditor on the given element, we try to get its container until null or an
		// editable intent element is found
		boolean canBeOpenedByIntentEditor = IntentHelper.canBeOpenedByIntentEditor(intentElementToOpen);
		EObject elementToOpen = null;
		if (intentElementToOpen instanceof EObject) {
			elementToOpen = (EObject)intentElementToOpen;
		}
		while (!canBeOpenedByIntentEditor && elementToOpen != null && !(elementToOpen instanceof Resource)) {
			elementToOpen = elementToOpen.eContainer();
			canBeOpenedByIntentEditor = IntentHelper.canBeOpenedByIntentEditor(elementToOpen);
		}

		if (canBeOpenedByIntentEditor) {

			IntentEditorInput input = new IntentEditorInput(elementToOpen);

			IntentEditor editor = (IntentEditor)page.openEditor(input, IntentEditorActivator.EDITOR_ID);
			((IntentDocumentProvider)editor.getDocumentProvider()).addRepositoryObjectHandler(handler);
			((IntentDocumentProvider)editor.getDocumentProvider()).setRepository(repository);
			((IntentDocumentProvider)editor.getDocumentProvider()).setEditor(editor);
			return editor;
		} else {
			IntentUiLogger.logError("this element is not a correct Intent element", new PartInitException(
					"Invalid element : must be a Intent Element"));
			IStatus status = new Status(IStatus.ERROR, IntentEditorActivator.PLUGIN_ID,
					"this element is not a correct Intent element");

			throw new PartInitException(status);
		}

	}
}
