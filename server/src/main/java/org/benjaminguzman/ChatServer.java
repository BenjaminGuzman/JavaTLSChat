/*
 * Copyright (c) 2021. Benjamín Antonio Velasco Guzmán
 * Author: Benjamín Antonio Velasco Guzmán <9benjaminguzman@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.benjaminguzman;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.management.InstanceAlreadyExistsException;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Loggers;
import org.benjaminguzman.exceptions.InvalidClientRequest;
import org.benjaminguzman.types.ClientRequestType;
import org.benjaminguzman.types.ServerResponseType;
import org.jetbrains.annotations.NotNull;

public class ChatServer implements Runnable {
	private final ExecutorService threadPool; // thread pool to use when creating a new listener

	private ServerSocket serverSocket;
	private final ConcurrentHashMap<ChatUser, ChatSocket> connectedClients; // maps user object -> chat socket

	private final int SERVER_PORT;
	private final InetAddress SERVER_ADDRESS;
	private final int SOCKET_TIMEOUT;

	private final Logger logger;

	/**
	 * Creates the chat server with the default port 12365 and the localhost address of the system
	 * And a default socket timeout of 5minutes
	 *
	 * See {@link #init()} to create the actual server socket
	 */
	public ChatServer() throws UnknownHostException {
		this(12365);
	}

	/**
	 * Creates the chat server with the specified port and the localhost address of the system
	 * And a default socket timeout of 5 minutes
	 *
	 * See {@link #init()} to create the actual server socket
	 *
	 * @param server_port the port for the server binding
	 */
	public ChatServer(int server_port) throws UnknownHostException {
		this(server_port, InetAddress.getLocalHost());
	}

	/**
	 * Creates the chat server with the specified port and specified address
	 * And a default socket timeout of 5 minutes
	 *
	 * See {@link #init()} to create the actual server socket
	 *
	 * @param server_port the port for the server binding
	 * @param address the address for the server binding
	 */
	public ChatServer(int server_port, @NotNull InetAddress address) {
		this(server_port, address, 60_000 /* 1m */ * 5 /* 5min. */);
	}

	/**
	 * Creates the chat server using the specified parameters
	 *
	 * See {@link #init()} to create the actual server socket
	 *
	 * @param server_port the port for the server binding
	 * @param address the address for the server binding
	 * @param socket_timeout the socket timeout in milliseconds
	 */
	public ChatServer(int server_port, @NotNull InetAddress address, int socket_timeout) {
		this.SERVER_PORT = server_port;
		this.SERVER_ADDRESS = address;
		this.SOCKET_TIMEOUT = socket_timeout;

		this.connectedClients = new ConcurrentHashMap<>(3);

		this.threadPool = Executors.newCachedThreadPool();
		this.logger = LogManager.getLogger(this.getClass());
	}

	/**
	 * Initiates the server socket using a simple non-encrypted TCP socket factory
	 *
	 * Use {@link #init(InputStream, char[])} to create a socket with TLS support
	 */
	public void init() throws IOException {
		this.logger.warn("Initializing the server socket WITHOUT TLS support");

		// 50 as backlog is the default
		// see https://www.jguru.com/faq/view.jsp?EID=88087
		this.serverSocket = ServerSocketFactory.getDefault().createServerSocket(
			this.SERVER_PORT,
			50,
			this.SERVER_ADDRESS
		);
	}

	/**
	 * Initiates the server socket with the specified
	 * @param keyStoreIS the input stream where the keystore file is stored.
	 *                   The keystore should contain the private key and cert file needed to create an TLS socket
	 * @param keyStorePass the password for that keystore
	 */
	public void init(InputStream keyStoreIS, char[] keyStorePass) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, KeyManagementException {
		// load the keystore containing the private key and cert file
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(keyStoreIS, keyStorePass);

		// initialize the key manager to use within the ssl context
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
		keyManagerFactory.init(keyStore, keyStorePass);

		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

		this.serverSocket = sslContext.getServerSocketFactory().createServerSocket(
			this.SERVER_PORT,
			50,
			this.SERVER_ADDRESS
		);
	}

	/**
	 * Waits for a client to connect
	 *
	 * When a client is connected all the messaging/chatting stuff will be handled by this method
	 *
	 * This method will also use a thread pool to run again this method to accept new clients
	 */
	@Override
	public void run() {
		String clientAddr = null;
		try (Socket clientSocket = this.serverSocket.accept()) {
			// this is sort of a recursive way to do a loop and wait for connections
			this.newListener();

			clientSocket.setSoTimeout(this.SOCKET_TIMEOUT);
			clientAddr = clientSocket.getInetAddress().toString();

			ChatSocket clientChatSocket = new ChatSocket(clientSocket);

			// handle the new connection
			this.handleNewClientConnected(clientChatSocket);

			// if everything went good, start receiving and sending messages
			while (!clientChatSocket.isClosed())
				this.handleClientRequest(clientChatSocket);
		} catch (SocketTimeoutException e) {
			System.out.println("The socket from " + clientAddr + " has timed out. Connection was closed");
			e.printStackTrace();
			// TODO: REMOVE CLIENT SOCKET FROM THE HASHMAP
		} catch(InvalidClientRequest e) {
			System.err.println("The client from " + clientAddr + " sent and invalid request!!");
			e.printStackTrace();
		} catch (SSLException e) {
			e.printStackTrace();
			System.err.println("Probably the above exception occurred because someone tried to connect " +
				"trough a non-SSL channel (e. g. HTTP instead of HTTPS)");
			System.out.println("If this were a web server, an HTTP server should be listening and " +
				"redirecting to the HTTPS server");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Uses the {@link #threadPool} to runa new thread using this class (that implements {@link Runnable})
	 * to listen for new incoming connections
	 *
	 * The {@link #run()} method will automatically invoke this method again so there is no need to invoke it again
	 * from outside this class
	 */
	public void newListener() {
		this.threadPool.execute(this);
	}

	/**
	 * Handles the new client connected
	 *
	 * This method will first read the headers and if valid, the clientSocket will be added to the
	 * {@link #connectedClients} hashmap
	 *
	 * @param clientSocket the client socket
	 */
	private void handleNewClientConnected(@NotNull ChatSocket clientSocket) throws IOException {
		// first line should contain the request type header
		String reqTypeHeader = clientSocket.readLine();

		ClientRequestType reqType = ClientRequestType.fromHeader(reqTypeHeader);
		if (reqType != ClientRequestType.CONNECT)
			throw new InvalidClientRequest("First request sent from the client SHOULD be a connect request");

		// read next line that should contain the username hash
		String username = clientSocket.readLine();

		ChatUser connectedUser = new ChatUser(username, clientSocket.getClientSocket().getInetAddress());

		// notify all other users about the new connected user
		this.broadCastUserConnected(connectedUser);

		// add the new connected user to the hashmap
		this.connectedClients.put(connectedUser, clientSocket);
		clientSocket.setOnCloseHook(() -> this.connectedClients.remove(connectedUser));

		// notify the user about the successful connection
		clientSocket.writeLines(
			ServerResponseType.CONNECTION_SUCCESS.getHeader(),
			String.valueOf(connectedUser.hashCode())
		);
	}

	/**
	 * this method will handle
	 * {@link ClientRequestType#MESSAGE}
	 * {@link ClientRequestType#LOGOUT}
	 * {@link ClientRequestType#CONNECTED_USERS}
	 * events
	 * @param clientSocket the client socket
	 */
	private void handleClientRequest(@NotNull ChatSocket clientSocket) throws IOException {
		String reqTypeHeader = clientSocket.readLine();

		ClientRequestType reqType = ClientRequestType.fromHeader(reqTypeHeader);
		if (reqType == ClientRequestType.LOGOUT) {
			// close the socket, if the close hook is configured,
			// the socket will be automatically removed from the hashmap
			clientSocket.close();
		} else if (reqType == ClientRequestType.MESSAGE) {
			// read to ID
			String toID = clientSocket.readLine();
			int to_id = Integer.parseInt(toID);

			// read beginning message header
			String messageHeader = clientSocket.readLine(); // just ignore that header

			// read the actual message, it should be base64-encoded
			// and should be decoded in the receiving client
			String message = clientSocket.readLine();

			// read the message footer
			String messageFooter = clientSocket.readLine(); // just ignore the footer

			// forward data to the right client
			// get the right client
			// the to id is actually the address of the client
			// TODO: make this work with ipv6 too!
			byte[] address = {
				(byte) (to_id >> 24 & 0xff),
				(byte) (to_id >> 16 & 0xff),
				(byte) (to_id >> 8 & 0xff),
				(byte) (to_id & 0xff)
			};
			ChatSocket receiverSocket = this.connectedClients.get(new ChatUser(
				"", Inet4Address.getByAddress(address)
			));
			if (receiverSocket == null) {
				logger.error(
					"The id {} is not in the hashmap of connected clients" +
						" and client {} (hash code: {}) asked me to forward a message {}",
					to_id,
					clientSocket.getClientSocket().getInetAddress().getHostAddress(),
					clientSocket.getClientSocket().getInetAddress().hashCode(),
					message
				);
				return;
			}
			receiverSocket.writeLines(
				ServerResponseType.MESSAGE.getHeader(), // message header
				String.valueOf(clientSocket.getClientSocket().getInetAddress().hashCode()), // sender ID
				toID, // receiver ID
				messageHeader,
				message, // this should be Base64-encoded
				messageFooter
			);
		} else if (reqType == ClientRequestType.CONNECTED_USERS) {
			clientSocket.writeLines(
				ServerResponseType.CONNECTED_USERS.getHeader(),
				"---BEGIN LIST---"
			);
			clientSocket.writeLines(
				this.connectedClients.keySet().stream()
					.map(chatUser -> chatUser.hashCode() + ":" + chatUser.getUsername())
					.toArray(String[]::new)
			);
			clientSocket.writeLines("---END LIST---");
		} else
			throw new InvalidClientRequest(reqTypeHeader + " is an invalid header!!");
	}

	/**
	 * Broadcasts a {@link ServerResponseType#USER_CONNECTED} event to all connected sockets
	 * @param newConnectedUser the object for the new connected user
	 */
	private void broadCastUserConnected(@NotNull ChatUser newConnectedUser) {
		this.connectedClients.keySet().forEach(chatUser -> {
			try {
				// remove the closed sockets
				// (just in case the close hook didn't work)
				ChatSocket socket = this.connectedClients.get(chatUser);
				if (socket.isClosed()) {
					this.connectedClients.remove(chatUser);
					return;
				}

				socket.writeLines(
					// write header
					ServerResponseType.USER_CONNECTED.getHeader(),

					// write connected user information
					String.valueOf(newConnectedUser.hashCode()),
					newConnectedUser.getUsername()
				);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}
}
