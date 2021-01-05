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

package org.benjaminguzman.types;

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
	 *
	 * @see ServerResponseFailedReason for details
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
	 * <p>
	 * Where 1 is the id for the user SENDING the message, the SENDER
	 * 2 is the id for the user RECEIVING the message, the RECEIVER
	 * <p>
	 * Hello world is simple the message
	 */
	MESSAGE("MESSAGE"),

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
}
