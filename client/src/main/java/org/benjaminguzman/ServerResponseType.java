package org.benjaminguzman;

public enum ServerResponseType {
	/**
	 * The user wanted to connect, but something failed
	 * <p>
	 * Therefore the server will respond with a CONNECTION_FAILED response
	 * <p>
	 * An example of the response is
	 * <p>
	 * CONNECTION_FAILED\nUNAUTHORIZED
	 * <p>
	 * Where UNAUTHORIZED provides more details to the user about why the connection failed
	 */
	CONNECTION_FAILED("CONNECTION_FAILED"),

	/**
	 * The user successfully connected and authenticated with the chat server
	 * <p>
	 * An example of the response is
	 * <p>
	 * CONNECTION_SUCCESS\n10
	 * <p>
	 * Where 10 is the id for to identify the user in the chat server
	 */
	CONNECTION_SUCCESS("CONNECTION_SUCCESS"),

	/**
	 * The server is forwarding a message received from some user
	 * <p>
	 * An example of the response is
	 *
	 * MESSAGE\n---BEGIN MESSAGE---\nabc=\n--END MESSAGE---
	 * <p>
	 * abc= is the base64 encoded message, it is encoded to avoid confusions,
	 * e. g. when a user writes ---BEGIN MESSAGE--
	 */
	MESSAGE("MESSAGE"),

	/**
	 * The server is sending the list of connected users
	 *
	 * An example of the response is
	 *
	 * CONNECTED_USERS\n---BEGIN LIST---\n23408:base54username1\n789456:base64username2\n---END LIST---
	 *
	 * Each username gill be given separated by a line separator and the following format
	 * -user chat id-:-username base64-encoded-
	 *
	 * the username is base64 encoded to avoid confusions,
	 * e. g. if some username is ---END LIST---
	 */
	CONNECTED_USERS("CONNECTED_USERS"),

	/**
	 * A new client connected to the server
	 * <p>
	 * The server needs to notify everyone else about that, therefore the server will broadcast
	 * that event to all connected clients
	 */
	USER_CONNECTED("USER_CONNECTED");

	private final String header;

	ServerResponseType(String header) {
		this.header = header;
	}

	/**
	 * @return the string representing the header. Use this to write into the socket
	 */
	public String getHeader() {
		return header;
	}

	/**
	 * Get the corresponding enum value from the given string header
	 *
	 * @param header the header could be in either uppercase or lowercase, it doesn't
	 *               matter, this method will ignore case
	 * @return the corresponding {@link ServerResponseType} if found, if not null is returned
	 */
	public static ServerResponseType fromHeader(String header) {
		for (ServerResponseType reqType : ServerResponseType.values())
			if (reqType.getHeader().equalsIgnoreCase(header))
				return reqType;

		return null;
	}
}
