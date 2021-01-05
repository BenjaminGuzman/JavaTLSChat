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

import java.net.InetAddress;
import org.jetbrains.annotations.NotNull;

public class ChatUser {
	private boolean hash_code_cached;
	private int hash_code;

	@NotNull
	private final String username;

	@NotNull
	private final InetAddress connAddress;

	public ChatUser(@NotNull String username, @NotNull InetAddress connAddress) {
		this.username = username;
		this.connAddress = connAddress;
	}

	public @NotNull String getUsername() {
		return username;
	}

	/**
	 * @return the hashcode of the inet address for this user
	 * This is done that way
	 * 1.- because it is good to avoid collisions
	 * 2.- if you use a hashmap looking for a user with a certain inet address
	 *     will be the same as looking for just the inet address
	 */
	@Override
	public int hashCode() {
		// cache the hashcode if it is not already cached
		if (!this.hash_code_cached) {
			this.hash_code = this.connAddress.hashCode();
			this.hash_code_cached = true;
		}

		return this.hash_code;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		ChatUser chatUser = (ChatUser) o;

		return hash_code == chatUser.hash_code;
	}

	@Override
	public String toString() {
		return "ChatUser{" +
			"hash_code=" + hash_code +
			", username='" + username + '\'' +
			'}';
	}
}
