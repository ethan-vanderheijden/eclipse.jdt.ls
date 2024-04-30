package org.eclipse.jdt.ls.core.internal.concurrent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TCPServer {
	public static final String CONTENT_LENGTH_HEADER = "Content-Length";
	public static final String CONTENT_TYPE_HEADER = "Content-Type";

	private final SwappableInputStream languageServerIn;
	private final SwappableOutputStream languageServerOut;

	public TCPServer() {
		languageServerIn = new SwappableInputStream();
		languageServerOut = new SwappableOutputStream();

		Thread serverThread = new Thread(this::startServer);
		serverThread.start();
	}

	private void startServer() {
		System.out.println("Starting tcp server");
		try (ServerSocket server = new ServerSocket(4004, 5, InetAddress.getByName("127.0.0.1"))) {
			while (true) {
				Socket conn = server.accept();
				System.out.println("Got connection");
				conn.setKeepAlive(true);

				BufferedInputStream input = new BufferedInputStream(conn.getInputStream());
				BufferedOutputStream output = new BufferedOutputStream(conn.getOutputStream());
				languageServerIn.setIn(input);
				languageServerOut.setOut(output);
				languageServerIn.blockWhileAlive();

				System.out.println("TCP connection closed");
				conn.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public InputStream getIn() {
		return languageServerIn;
	}

	public OutputStream getOut() {
		return languageServerOut;
	}
}
