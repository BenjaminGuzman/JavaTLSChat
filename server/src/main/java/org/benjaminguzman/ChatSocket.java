package org.benjaminguzman;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatSocket implements AutoCloseable {
	private final Socket clientSocket;
	private final BufferedWriter socketBuffOutStream;
	private final BufferedReader socketBuffInStream;

	public Runnable onClose;

	public ChatSocket(Socket clientSocket) throws IOException {
		this.clientSocket = clientSocket;

		this.socketBuffInStream = new BufferedReader( // improve performance with a buffer
			new InputStreamReader( // decode raw bytes to the default charset
				this.clientSocket.getInputStream(),
				StandardCharsets.UTF_8
			)
		);

		this.socketBuffOutStream = new BufferedWriter( // improve performance with a buffer
			new OutputStreamWriter( // encode raw bytes to the default charset
				this.clientSocket.getOutputStream(),
				StandardCharsets.UTF_8
			)
		);
	}

	/**
	 * Writes multiple lines to the socket, by appending a line separator after each written string
	 *
	 * This method will call flush on the stream at the end, so data will be sent
	 *
	 * @param lines the lines you want to write in the socket stream. These lines may not contain a new line
	 *              as it will be added by this method automatically when writing to the buffer
	 * @return the same object. Just for convenience so you can chain calls to other methods
	 * @throws IOException if there was an error writing to the buffer
	 */
	public ChatSocket writeLines(String... lines) throws IOException {
		for (String line : lines) {
			this.socketBuffOutStream.write(line);
			this.socketBuffOutStream.newLine();
		}

		this.socketBuffOutStream.flush();

		return this;
	}

	/**
	 * @return The original {@link Socket} object. The same used as argument in the
	 * {@link #ChatSocket(Socket)} constructor
	 */
	public Socket getClientSocket() {
		return clientSocket;
	}

	/**
	 * This method behaves exacly as {@link BufferedReader#readLine()}
	 * @return the line read or null if EOF is reached, see {@link BufferedReader#readLine()} for more
	 * @throws IOException if there was an error reading the line
	 */
	public String readLine() throws IOException {
		return this.socketBuffInStream.readLine();
	}

	public BufferedWriter getSocketBuffOutStream() {
		return socketBuffOutStream;
	}

	public BufferedReader getSocketBuffInStream() {
		return socketBuffInStream;
	}

	/**
	 * @return true if the socket is closed, this could be because you closed it or because EOF has been reached
	 */
	public boolean isClosed() {
		boolean eof_reached = false;

		try {
			// read from the stream to check if we've reached the EOF
			// this operation should not modify the current position (pointer) in the buffer
			// therefore a mark and reset is used

			// mark position previous to reading from the stream, so we can go reset the pointer
			this.socketBuffInStream.mark(1);
			eof_reached = this.socketBuffInStream.read() == - 1;
			this.socketBuffInStream.reset(); // reset the pointer
		} catch (IOException e) {
			if ("Stream closed".equalsIgnoreCase(e.getMessage()) || "Socket closed".equalsIgnoreCase(e.getMessage()))
				return true;
			if (!"Connection reset".equalsIgnoreCase(e.getMessage()))
				e.printStackTrace();
		}

		return this.clientSocket.isClosed() || eof_reached;
	}

	/**
	 * Adds a hook to execute automatically when {@link #close()} is called
	 * @param onClose the hook to execute
	 */
	public void setOnCloseHook(Runnable onClose) {
		this.onClose = onClose;
	}

	/**
	 * Will close the in/out streams opened from the socket
	 *
	 * Note: the first thing this method will do is execute the on close hook if it was set via {@link #setOnCloseHook(Runnable)}
	 * this is done to ensure it is executed even in subsequent calls to close other resources fail
	 *
	 * And then the socket itself will be closed
	 * @throws IOException if something bad happens while closing everything
	 */
	@Override
	public void close() throws IOException {
		if (this.onClose != null)
			this.onClose.run();

		this.socketBuffOutStream.close();
		this.socketBuffInStream.close();
		this.clientSocket.close();
	}
}
