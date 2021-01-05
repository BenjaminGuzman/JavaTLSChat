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

public enum ClientRequestType {
	/**
	 * The user wants to create a brand-new connection to the chat server
	 * <p>
	 * Therefore the client should sent only 1 request of this type during all the socket lifetime
	 * <p>
	 * An example of the request the client should send is
	 * <p>
	 * CONNECT\na0b1c2d3e4f
	 * <p>
	 * Where a0b1c2d3e4f is the authentication hash, this way the server knows the server has been previously
	 * authenticated
	 */
	CONNECT("CONNECT"),

	/**
	 * The user wants to send a message to someone
	 * <p>
	 * The client can send multiple requests of this type
	 * <p>
	 * An example of the request the client should send is
	 * <p>
	 * MESSAGE\n1\n2\nHello world
	 * <p>
	 * Where 1 is the id for the user SENDING the message, the SENDER
	 * 2 is the id for the user RECEIVING the message, the RECEIVER
	 * <p>
	 * Hello world is simple the message
	 */
	MESSAGE("MESSAGE"),

	/**
	 * The client is asking for the list of connected users
	 *
	 * An example of the request is
	 *
	 * CONNECTED_USERS
	 */
	CONNECTED_USERS("CONNECTED_USERS"),

	/**
	 * The user wants lo logout from the chat service
	 * <p>
	 * An example of the request the client should send is
	 * <p>
	 * LOGOUT
	 */
	LOGOUT("LOGOUT");

	private final String header;

	ClientRequestType(String header) {
		this.header = header;
	}

	/**
	 * Get the corresponding enum value from the given string header
	 *
	 * @param header
	 * 	the header, this could be "message" or "connect" in either uppercase or lowercase, it doesn't
	 * 	matter, this method will ignore case
	 *
	 * @return the corresponding {@link ClientRequestType} if found, if not null is returned
	 */
	public static ClientRequestType fromHeader(String header) {
		for (ClientRequestType reqType : ClientRequestType.values())
			if (reqType.getHeader().equalsIgnoreCase(header))
				return reqType;

		return null;
	}

	public String getHeader() {
		return header;
	}

	@Override
	public String toString() {
		return "ClientRequestType{" +
			"header='" + header + '\'' +
			'}';
	}
}
