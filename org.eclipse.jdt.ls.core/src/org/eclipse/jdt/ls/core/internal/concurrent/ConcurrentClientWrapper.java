package org.eclipse.jdt.ls.core.internal.concurrent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.ProgressReport;
import org.eclipse.jdt.ls.core.internal.StatusReport;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.MessageParams;

public class ConcurrentClientWrapper implements InvocationHandler {
	private JavaLanguageClient delegate;

	public ConcurrentClientWrapper(JavaLanguageClient delegate) {
		this.delegate = delegate;
	}

	public static JavaLanguageClient wrap(JavaLanguageClient delegate) {
		return (JavaLanguageClient) Proxy.newProxyInstance(
			JavaLanguageClient.class.getClassLoader(),
			new Class[]{JavaLanguageClient.class},
			new ConcurrentClientWrapper(delegate)
		);
	}

	// all of these are optional notifications not part of LSP
	void sendStatusReport(StatusReport report) {}
	void sendActionableNotification(ActionableNotification notification) {}
	void sendEventNotification(EventNotification notification) {}
	void sendProgressReport(ProgressReport report) {}
	
	void telemetryEvent(Object object) {}
	void logTrace(LogTraceParams params) {}
	void logMessage(MessageParams message) {}

	CompletableFuture<Object> executeClientCommand(ExecuteCommandParams params) {
		// not implemented client side anyway
		if (params.getCommand().equals("_java.reloadBundles.command")) {
			return CompletableFuture.completedFuture(Collections.<String>emptyList());
		}
		return delegate.executeClientCommand(params);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Method resolvedMethod = findMethod(this.getClass(), method);
		if (resolvedMethod != null) {
			return resolvedMethod.invoke(this, args);
		}
		resolvedMethod = findMethod(delegate.getClass(), method);
		if (resolvedMethod != null) {
			return resolvedMethod.invoke(delegate, args);
		}
		return null;
	}

	private Method findMethod(Class<?> clazz, Method method) throws Throwable {
		try {
			return clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
		} catch (NoSuchMethodException e) {
			return null;
		}
	}
}
