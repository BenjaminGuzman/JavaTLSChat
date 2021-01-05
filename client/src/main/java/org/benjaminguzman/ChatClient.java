package org.benjaminguzman;

import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javax.net.SocketFactory;
import org.jetbrains.annotations.NotNull;

import javax.management.InstanceAlreadyExistsException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ChatClient extends Thread {
	private static ChatClient instance;

	@NotNull
	private ResourceBundle messagesBundle;

	private int chat_uid;

	private ChatSocket socket;

	@NotNull
	private final HashMap<Integer, ChatUser> connectedUsers; // maps the chat uid -> chat user
	// no need of a ConcurrentHashMap as this is the only class (and thread) that should modify the object
	// NOTE: in the current architecture the key is actually the IP address of the connected user

	@NotNull
	private final ServerConf serverConf;
	@NotNull
	private final String username;

	// callbacks
	// first param is the id from the sender, second param is the actual message
	@NotNull
	private BiConsumer<Integer, String> onMessage;
	@NotNull
	private final Consumer<String> onError;
	@NotNull
	private Consumer<ChatUser> onUserConnected;
	@NotNull
	private Consumer<String> onConnectionFailed;
	@NotNull
	private Runnable onConnectionSuccess;
	@NotNull
	private Consumer<HashMap<Integer, ChatUser>> onConnectedUsersList;

	/**
	 * Creates a new chat client
	 *
	 * @throws InstanceAlreadyExistsException if this class was already instantiated
	 */
	public ChatClient(
		ServerConf serverConf,
		@NotNull String username,
		@NotNull BiConsumer<Integer, String> onMessage,
		@NotNull Consumer<ChatUser> onUserConnected,
		@NotNull Consumer<String> onError,
		@NotNull Runnable onConnectionSuccess,
		@NotNull Consumer<String> onConnectionFailed,
		@NotNull Consumer<HashMap<Integer, ChatUser>> onConnectedUsersList
	) throws InstanceAlreadyExistsException {
		if (ChatClient.instance != null)
			throw new InstanceAlreadyExistsException();

		this.serverConf = Objects.requireNonNull(serverConf);
		this.username = Objects.requireNonNull(username);
		this.onMessage = Objects.requireNonNull(onMessage);
		this.onUserConnected = Objects.requireNonNull(onUserConnected);
		this.onConnectionSuccess = Objects.requireNonNull(onConnectionSuccess);
		this.onConnectionFailed = Objects.requireNonNull(onConnectionFailed);
		this.onConnectedUsersList = Objects.requireNonNull(onConnectedUsersList);
		this.onError = onError;

		this.setDaemon(true); // the client thread SHOULD be a daemon thread, NOT a user thread

		ChatClient.instance = this;
		this.messagesBundle = ResourceBundle.getBundle("messages", Locale.getDefault());
		this.connectedUsers = new HashMap<>();
	}

	/**
	 * Initialize the socket
	 *
	 * The socket may be an SSL socket or a plain socket depending on {@link #serverConf}
	 */
	private void init() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
		if (this.isInterrupted())
			return;

		Socket socket;

		if (this.serverConf.use_tls) {
			// create an SSL socket & make it trust the cert file
			SSLSocketFactory factory = this
				.configuredSSLContext("/resources/cert.pem")
				.getSocketFactory();

			SSLSocket sslSocket = (SSLSocket) factory
				.createSocket(this.serverConf.address, this.serverConf.port);

			sslSocket.startHandshake();
			socket = sslSocket;
		} else
			socket = SocketFactory.getDefault().createSocket(this.serverConf.address, this.serverConf.port);

		this.socket = new ChatSocket(socket);
	}

	/**
	 * Configures the SSL context to accept a self-signed certificate
	 *
	 * @return an SSLContext object that will trust the given certificate
	 */
	private SSLContext configuredSSLContext(@NotNull String certFilePath) throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, KeyManagementException {
		Certificate certificate = CertificateFactory
			.getInstance("X.509")
			.generateCertificate(ChatClient.class.getResourceAsStream(certFilePath));

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setCertificateEntry("server", certificate);

		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
			TrustManagerFactory.getDefaultAlgorithm()
		);
		trustManagerFactory.init(keyStore);

		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

		return sslContext;
	}

	/**
	 * Sends the connect request and waits for a response from the server
	 * The response from the server will contain the {@link #chat_uid}, this method will initialize that value too
	 *
	 * @return true if the response from the server was good and the client connected successfully, false otherwise
	 * if it is false, you'll probably want to check the next line in the buffer to see why the connection failed
	 * @throws IOException if there was an error while writing to or reading from the socket
	 */
	private boolean sendConnectRequest() throws IOException {
		if (this.isInterrupted())
			return false;

		// send connect request to the chat server, this will start our own handshake with the server

		this.socket.writeLines(
			"CONNECT", // write CONNECT header
			username // write the username to use
		);

		// test if the connection was successful
		if (!ServerResponseType.CONNECTION_SUCCESS.getHeader().equalsIgnoreCase(this.socket.readLine()))
			return false;

		// if it was successful the server should have sent the chat id
		this.chat_uid = Integer.parseInt(this.socket.readLine());
		return true;
	}

	/**
	 * Use this method to simply send a message tto someone
	 * <p>
	 * This method is synchronized to avoid multiple threads sending a message at one time
	 *
	 * @param message the message you want to send
	 * @throws InterruptedException if the thread is interrupted
	 */
	synchronized public void sendMessage(String message, int to_id) throws InterruptedException {
		if (this.isInterrupted())
			throw new InterruptedException("The chat thread is interrupted");

		try {
			this.socket.writeLines(
				"MESSAGE", // write request header
				String.valueOf(to_id), // write TO_ID
				"---BEGIN MESSAGE---",
				Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8)), // write message
				"---END MESSAGE---"
			);
		} catch (IOException e) {
			this.onError.accept(this.messagesBundle.getString("chat_send_msg_error"));
		}
	}

	/**
	 * Requests the list of connected users from the server
	 * @throws InterruptedException if the thread was interrupted
	 */
	synchronized public void getConnectedUsers() throws InterruptedException {
		if (this.isInterrupted())
			throw new InterruptedException("The chat thread is interrupted");

		try {
			this.socket.writeLines("CONNECTED_USERS");
		} catch (IOException e) {
			this.onError.accept(this.messagesBundle.getString("chat_conn_clients_error"));
		}
	}

	/**
	 * Use it to logout from the Chat server
	 */
	synchronized public void logout() throws IOException {
		this.socket.writeLines("LOGOUT");
		this.socket.close();
	}

	@Override
	public void interrupt() {
		super.interrupt();
		try {
			this.logout();
		} catch (IOException e) {
			e.printStackTrace();
		}
		ChatClient.instance = null;
	}

	@Override
	public void run() {
		// start TCP connection to the server
		try {
			this.init();
		} catch (KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
			// TODO: HANDLE EXCEPTION
			// exceptions are thrown if there is an error with the JVM
			// e. g. it doesn't support TLS, don't have compatible algorithms with the server, etc...
			e.printStackTrace();
			this.onError.accept(this.messagesBundle.getString("chat_init_error"));
			return;
		} catch (IOException e) {
			// TODO: HANDLE EXCEPTION
			// exception is likely to happen if there is an error with the certificate (unlikely)
			// or with the connectivity, e. g. if the server is DOWN
			e.printStackTrace();
			this.onError.accept(this.messagesBundle.getString("chat_conn_error"));
			return;
		}

		// start own-protocol connection
		try {
			if (!this.sendConnectRequest()) {
				// if the connection failed wee why and notify the user
				String failReason = this.socket.readLine();
				this.onConnectionFailed.accept(failReason);
				return;
			}
			this.onConnectionSuccess.run();
		} catch (IOException e) {
			// TODO: HANDLE EXCEPTION
			this.onError.accept(this.messagesBundle.getString("chat_init_error"));
			e.printStackTrace();
		}

		// block & process incoming data
		try {
			while (!this.socket.isClosed())
				this.handleServerResponses();
		} catch (IOException e) {
			e.printStackTrace();
			this.onError.accept(this.messagesBundle.getString("chat_send_msg_error"));
		}
	}

	/**
	 * Handles server responses, specifically
	 * {@link ServerResponseType#MESSAGE}
	 * {@link ServerResponseType#USER_CONNECTED}
	 * {@link ServerResponseType#CONNECTED_USERS}
	 *
	 * @throws IOException when error while reading from the socket
	 */
	private void handleServerResponses() throws IOException {
		String header = this.socket.readLine();

		ServerResponseType resType = ServerResponseType.fromHeader(header);

		if (resType == ServerResponseType.MESSAGE) {
			// read from ID
			int from_id = Integer.parseInt(this.socket.readLine());

			// skip to id because its your own chat_uid
			this.socket.readLine();

			this.socket.readLine(); // skip begin message header

			// read base64 message
			String message = new String(
				Base64.getDecoder().decode( // decode base64 message
					this.socket.readLine().getBytes(StandardCharsets.UTF_8)
				),
				StandardCharsets.UTF_8
			);

			this.socket.readLine(); // skipp end message header

			this.onMessage.accept(from_id, message);
		} else if (resType == ServerResponseType.USER_CONNECTED) {
			int new_user_chat_id = Integer.parseInt(this.socket.readLine());
			String username = this.socket.readLine();

			ChatUser connectedUser = new ChatUser(new_user_chat_id, username);

			this.connectedUsers.put(new_user_chat_id, connectedUser);
			this.onUserConnected.accept(connectedUser);
		} else if (resType == ServerResponseType.CONNECTED_USERS) {
			this.socket.readLine(); // ignore ---BEGIN LIST--- header

			this.connectedUsers.clear(); // clear the hashmap

			String line;
			String[] splittedString;

			int user_chat_uid;
			String user_username;
			while (!(line = this.socket.readLine()).equalsIgnoreCase("---END LIST---")) {
				splittedString = line.split(":", 2);
				if (splittedString.length != 2)
					continue; // discard malformed input (if exists)

				user_chat_uid = Integer.parseInt(splittedString[0]);
				user_username = splittedString[1];

				this.connectedUsers.put(user_chat_uid, new ChatUser(user_chat_uid, user_username));
				this.onConnectedUsersList.accept(this.connectedUsers);
			}
		}
	}

	/// SETTERS ///

	/**
	 * Set the on connection established callback
	 * <p>
	 * This should be set before calling {@link Thread#start()} on this object
	 * otherwise this will throw an exception
	 *
	 * @param onConnectionSuccess the callback to execute
	 * @throws IllegalStateException if the thread has started
	 */
	public void setOnConnectionSuccess(@NotNull Runnable onConnectionSuccess) {
		if (this.isAlive())
			throw new IllegalStateException("Thread is already running");

		this.onConnectionSuccess = Objects.requireNonNull(onConnectionSuccess);
	}

	/**
	 * Set the on connection established callback
	 * <p>
	 * This should be set before calling {@link Thread#start()} on this object
	 * otherwise this will throw an exception
	 *
	 * @param onMessage the callback to execute. The first param is the uid for the user sending the message
	 *                  the second param is the message
	 * @throws IllegalStateException if the thread has started
	 */
	public void setOnMessage(@NotNull BiConsumer<Integer, String> onMessage) {
		if (this.isAlive())
			throw new IllegalStateException("Thread is already running");

		this.onMessage = Objects.requireNonNull(onMessage);
	}

	/**
	 * Set the on connection failed callback
	 * <p>
	 * This should be set before calling {@link Thread#start()} on this object
	 * otherwise this will throw an exception
	 *
	 * @param onConnectionFailed the callback to execute. The parameter for the callback is a {@link String} telling
	 *                           the reason of the failed connection
	 * @throws IllegalStateException if the thread has started
	 */
	public void setOnConnectionFailed(@NotNull Consumer<String> onConnectionFailed) {
		if (this.isAlive())
			throw new IllegalStateException("Thread is already running");

		this.onConnectionFailed = Objects.requireNonNull(onConnectionFailed);
	}

	/**
	 * Set the on user connected callback
	 * <p>
	 * This should be set before calling {@link Thread#start()} on this object
	 * otherwise this will throw an exception
	 *
	 * @param onUserConnected the callback to execute. The parameter for the callback is a {@link ChatUser} with
	 *                        the info of the connected user
	 * @throws IllegalStateException if the thread has started
	 */
	public void setOnUserConnected(@NotNull Consumer<ChatUser> onUserConnected) {
		if (this.isAlive())
			throw new IllegalStateException("Thread is already running");

		this.onUserConnected = Objects.requireNonNull(onUserConnected);
	}

	/**
	 * Changes the message bundle to the given locale
	 * @param locale the locale of the messages
	 * @throws MissingResourceException if the .properties file does not exists for that locale
	 */
	public void changeMessagesBundle(@NotNull Locale locale) throws MissingResourceException {
		this.messagesBundle = ResourceBundle.getBundle("messages", locale);
	}

	public int getChatUID() {
		return chat_uid;
	}

	synchronized public static Thread getThread() {
		return ChatClient.instance;
	}

	public static class Builder {
		@NotNull
		private ServerConf serverConf = new ServerConf(InetAddress.getLocalHost(), 12365, false);
		@NotNull
		private String username = "Benjamín Guzmán";

		// callbacks/hooks
		@NotNull
		private Consumer<String> onError = System.err::println;
		@NotNull
		private Consumer<String> onConnectionFailed = System.err::println;
		@NotNull
		private BiConsumer<Integer, String> onMessage = (uid, msg) -> System.out.println("Message from " + uid + " \"" + msg + '"');
		@NotNull
		private Consumer<ChatUser> onUserConnected = System.out::println;
		@NotNull
		private Runnable onConnectionSuccess = () -> { };
		@NotNull
		private Consumer<HashMap<Integer, ChatUser>> onConnectedUsersList = System.out::println;

		public Builder() throws UnknownHostException {
		}


		/**
		 * Configuration of the server
		 * @param serverConf the configuration for the server
		 */
		public Builder serverConf(ServerConf serverConf) {
			this.serverConf = serverConf;
			return this;
		}

		/**
		 * Executed when there is an error (any type of error)
		 *
		 * The first argument is the String given details about the error
		 * That string is taken from the properties file
		 * @param onError the hook to execute
		 */
		public Builder onError(@NotNull Consumer<String> onError) {
			this.onError = onError;
			return this;
		}

		/**
		 * Executed when a client sends a message
		 *
		 * The first argument is the id of the client tha sent the message
		 * The second argument is the actual message sent
		 * @param onMessage the hook to execute
		 */
		public Builder onMessage(BiConsumer<Integer, String> onMessage) {
			this.onMessage = onMessage;
			return this;
		}

		/**
		 * Executed when a client has connected to the chat service
		 *
		 * The first argument is an object with the details of the new connected user
		 * @param onUserConnected the hook to execute
		 */
		public Builder onUserConnected(Consumer<ChatUser> onUserConnected) {
			this.onUserConnected = onUserConnected;
			return this;
		}

		/**
		 * Executed when the client connects successfully to the server
		 * @param onConnectionSuccess the hook to execute
		 */
		public Builder onConnectionSuccess(Runnable onConnectionSuccess) {
			this.onConnectionSuccess = onConnectionSuccess;
			return this;
		}

		/**
		 * Executed when the client tried to connect but something failed
		 *
		 * The first argument for the Consumer is the fail reason sent from the server
		 * Maybe you'd need to process that & give a more verbose explanation to the user
		 * @param onConnectionFailed the hook to execute
		 */
		public Builder onConnectionFailed(Consumer<String> onConnectionFailed) {
			this.onConnectionFailed = onConnectionFailed;
			return this;
		}

		/**
		 * Executed when the list client is fully updated
		 *
		 * NOTE: this is not the hook that will be executed when a user connects
		 * for that event exists {@link #onUserConnected(Consumer)}
		 *
		 * The first argument for the Consumer is the updated hashmap containing the connected users
		 * @param onConnectedUsersList the hook to execute
		 */
		public Builder onConnectedUsersList(Consumer<HashMap<Integer, ChatUser>> onConnectedUsersList) {
			this.onConnectedUsersList = onConnectedUsersList;
			return this;
		}

		/**
		 * Set the username to use within the chat
		 * @param username the username
		 */
		public Builder setUsername(String username) {
			this.username = username;
			return this;
		}

		public ChatClient createChatClient() throws InstanceAlreadyExistsException {
			return new ChatClient(
				serverConf,
				username,
				onMessage,
				onUserConnected,
				onError,
				onConnectionSuccess,
				onConnectionFailed,
				onConnectedUsersList
			);
		}
	}
}
