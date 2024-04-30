package org.eclipse.jdt.ls.core.internal.concurrent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.handlers.BaseInitHandler;
import org.eclipse.jdt.ls.core.internal.handlers.InlayHintsParameterMode;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.handlers.WorkspaceExecuteCommandHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.TelemetryManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.NotebookDocumentClientCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

public class ConcurrentLanguageServer extends JDTLanguageServer {
	private InitializeResult cachedInitializedResult = null;
	private boolean finishedInit = false;

	public ConcurrentLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager) {
		super(projects, preferenceManager);
	}

	public ConcurrentLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager, TelemetryManager telemetryManager) {
		super(projects, preferenceManager, telemetryManager);
	}

	public ConcurrentLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager, WorkspaceExecuteCommandHandler commandHandler) {
		super(projects, preferenceManager, commandHandler);
	}

	public ConcurrentLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager, WorkspaceExecuteCommandHandler commandHandler, TelemetryManager telemetryManager) {
		super(projects, preferenceManager, commandHandler, telemetryManager);
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		if (!validInitializationParams(params)) {
			System.out.println("Throwing error on initialization!");
			CompletableFuture<InitializeResult> errorResponse = new CompletableFuture<InitializeResult>();
			ResponseError error = new ResponseError(
					ResponseErrorCode.InvalidParams,
					"Initialization params don't conform to expected standards. Dynamic registration, refresh support, and several JDTLS options must be disabled.",
					null
			);
			errorResponse.completeExceptionally(new ResponseErrorException(error));
			return errorResponse;
		}
		
		if (cachedInitializedResult == null) {
			System.out.println("Normal initialization");
			CompletableFuture<InitializeResult> initializeResult = super.initialize(params);
			try {
				// future should already be resolved
				cachedInitializedResult = initializeResult.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			return initializeResult;
		} else {
			WorkspaceFoldersChangeEvent event = new WorkspaceFoldersChangeEvent(params.getWorkspaceFolders(), Collections.emptyList());
			didChangeWorkspaceFolders(new DidChangeWorkspaceFoldersParams(event));
		}
		return CompletableFuture.completedFuture(cachedInitializedResult);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer#didChangeWorkspaceFolders(org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams)
	 */
	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		for(WorkspaceFolder folder : params.getEvent().getAdded()) {
			System.out.println("Adding: " + folder.getUri());
		}
		for(WorkspaceFolder folder : params.getEvent().getRemoved()) {
			System.out.println("Removing: " + folder.getUri());
		}
		super.didChangeWorkspaceFolders(params);
	}

	@Override
	public void initialized(InitializedParams params) {
		// only let the first notification through or it will try to re-initialize services
		if (!finishedInit) {
			System.out.println("finished initialization");
			finishedInit = true;
			super.initialized(params);
		}
	}
	
	@Override
	public CompletableFuture<Object> shutdown() {
		// no single client can control the life cycle of the server
		// if you want to close the server, kill the process
		return CompletableFuture.completedFuture(new Object());
	}
	
	@Override
	public void exit() {
		// no single client can control the life cycle of the server
		// if you want to close the server, kill the process
	}

	@Override
	public void setTrace(SetTraceParams params) {
	}

	private boolean validInitializationParams(InitializeParams params) {
		Map<?, ?> options = JSONUtility.toModel(params.getInitializationOptions(), Map.class);
		if (!(options.get(BaseInitHandler.SETTINGS_KEY) instanceof Map<?, ?> settings)) {
			return false;
		}

		@SuppressWarnings("unchecked")
		Preferences prefs = Preferences.createFrom((Map<String, Object>) settings);
		if (prefs.isReferencesCodeLensEnabled() ||
			prefs.isImplementationsCodeLensEnabled() ||
			prefs.getInlayHintsParameterMode() != InlayHintsParameterMode.NONE) {
			return false;
		}
		
		ClientCapabilities capabilities = params.getCapabilities();
		WorkspaceClientCapabilities workspace = capabilities.getWorkspace();
		TextDocumentClientCapabilities text = capabilities.getTextDocument();
		NotebookDocumentClientCapabilities notebook = capabilities.getNotebookDocument();
		WindowClientCapabilities window = capabilities.getWindow();
		if (workspace.getConfiguration() ||
			window.getWorkDoneProgress()) {
			return false;
		}
		
		if (isDynamicRegistration(workspace.getDidChangeConfiguration()) ||
			isDynamicRegistration(workspace.getDidChangeWatchedFiles()) ||
			isDynamicRegistration(workspace.getSymbol()) ||
			isDynamicRegistration(workspace.getExecuteCommand()) ||
			isDynamicRegistration(workspace.getFileOperations()) ||

			isDynamicRegistration(text.getSynchronization()) ||
			isDynamicRegistration(text.getCompletion()) ||
			isDynamicRegistration(text.getHover()) ||
			isDynamicRegistration(text.getSignatureHelp()) ||
			isDynamicRegistration(text.getDeclaration()) ||
			isDynamicRegistration(text.getDefinition()) ||
			isDynamicRegistration(text.getTypeDefinition()) ||
			isDynamicRegistration(text.getImplementation()) ||
			isDynamicRegistration(text.getReferences()) ||
			isDynamicRegistration(text.getDocumentHighlight()) ||
			isDynamicRegistration(text.getDocumentSymbol()) ||
			isDynamicRegistration(text.getCodeAction()) ||
			isDynamicRegistration(text.getCodeLens()) ||
			isDynamicRegistration(text.getDocumentLink()) ||
			isDynamicRegistration(text.getColorProvider()) ||
			isDynamicRegistration(text.getFormatting()) ||
			isDynamicRegistration(text.getRangeFormatting()) ||
			isDynamicRegistration(text.getOnTypeFormatting()) ||
			isDynamicRegistration(text.getRename()) ||
			isDynamicRegistration(text.getFoldingRange()) ||
			isDynamicRegistration(text.getSelectionRange()) ||
			isDynamicRegistration(text.getLinkedEditingRange()) ||
			isDynamicRegistration(text.getCallHierarchy()) ||
			isDynamicRegistration(text.getSemanticTokens()) ||
			isDynamicRegistration(text.getMoniker()) ||
			isDynamicRegistration(text.getTypeHierarchy()) ||
			isDynamicRegistration(text.getInlineValue()) ||
			isDynamicRegistration(text.getInlayHint()) ||
			isDynamicRegistration(text.getDiagnostic()) ||

			isDynamicRegistration(notebook.getSynchronization())) {
			return false;
		}
		
		if ((workspace.getInlayHint() != null && workspace.getInlayHint().getRefreshSupport()) ||
			(workspace.getCodeLens() != null && workspace.getCodeLens().getRefreshSupport()) ||
			(workspace.getSemanticTokens() != null && workspace.getSemanticTokens().getRefreshSupport()) ||
			(workspace.getInlineValue() != null && workspace.getInlineValue().getRefreshSupport()) ||
			(workspace.getDiagnostics() != null && workspace.getDiagnostics().getRefreshSupport())) {
			return false;
		}
		
		return true;
	}
	
	private boolean isDynamicRegistration(DynamicRegistrationCapabilities capability) {
		return capability != null && capability.getDynamicRegistration();
	}
}
