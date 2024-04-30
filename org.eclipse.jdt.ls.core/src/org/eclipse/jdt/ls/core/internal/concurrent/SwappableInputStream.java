package org.eclipse.jdt.ls.core.internal.concurrent;

import java.io.IOException;
import java.io.InputStream;

public class SwappableInputStream extends InputStream {
	private final Object inChange = new Object();
	private InputStream in;
	private boolean closed = false;

	public SwappableInputStream() {
		in = null;
	}

	public SwappableInputStream(InputStream in) {
		this.in = in;
	}

	public void setIn(InputStream in) {
		if (closed && in != null) {
			throw new IllegalStateException("Stream closed");
		}
		synchronized (inChange) {
			this.in = in;
			inChange.notifyAll();
		}
	}

	public void blockWhileAlive() {
		try {
			synchronized (inChange) {
				InputStream originalSource = in;
				if (originalSource != null) {
					while (in == originalSource) {
						inChange.wait();
					}
				}
			}
		} catch (InterruptedException ignored) {}
	}

	@Override
	public int read() throws IOException {
		return readWhenPossible(() -> in.read(), this::read);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return readWhenPossible(() -> in.read(b, off, len), () -> read(b, off, len));
	}

	@Override
	public int available() throws IOException {
		if (in == null) {
			return 0;
		}
		return in.available();
	}

	@Override
	public long skip(long n) throws IOException {
		if (in == null) {
			return 0;
		}
		return in.skip(n);
	}

	@Override
	public void close() throws IOException {
		synchronized (inChange) {
			closed = true;
			if (in != null) {
				in.close();
			}
			setIn(null);
		}
	}

	private interface ReadSupplier<T> {
		T get() throws IOException;
	}

	private int readWhenPossible(ReadSupplier<Integer> onRead, ReadSupplier<Integer> onInClosed) throws IOException {
		try {
			synchronized (inChange) {
				while(in == null && !closed) {
					inChange.wait();
				}
				if (closed) {
					throw new IOException("Stream closed");
				}
				int data = onRead.get();
				if (data == -1) {
					in.close();
					setIn(null);
					return onInClosed.get();
				} else {
					return data;
				}
			}
		} catch (InterruptedException e) {
			throw new RuntimeException("Shouldn't interrupt while waiting on read");
		}
	}
}
