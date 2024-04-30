package org.eclipse.jdt.ls.core.internal.concurrent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SwappableOutputStream extends OutputStream {
	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	private OutputStream out;
	private boolean closed = false;

	public SwappableOutputStream() {
		out = null;
	}

	public SwappableOutputStream(OutputStream out) {
		this.out = out;
	}

	public void setOut(OutputStream out) {
		try {
			buffer.writeTo(out);
			buffer.reset();
			this.out = out;
		} catch (IOException ignored) {
			// output stream is bad, ignore it
		}
	}

	@Override
	public void write(int b) throws IOException {
		if (closed) {
			throw new IOException("Stream closed");
		}
		if (out == null) {
			buffer.write(b);
		} else {
			out.write(b);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (closed) {
			throw new IOException("Stream closed");
		}
		if (out == null) {
			buffer.write(b, off, len);
		} else  {
			out.write(b, off, len);
		}
	}

	@Override
	public void flush() throws IOException {
		if (out != null) {
			out.flush();
		}
	}

	@Override
	public void close() throws IOException {
		if (out != null) {
			out.close();
			out = null;
		}
		buffer.reset();
		closed = true;
	}
}
