package org.benjaminguzman;

import org.jetbrains.annotations.NotNull;

/**
 * Wrapper class containing information about a connected user in the chat
 */
public class ChatUser {
	// the chat UID, this is used to identify to who/from a message is sent/received
	private final int chat_uid;

	// the username of the user associated with the chatUID
	@NotNull
	private final String userName;

	public ChatUser(int chat_uid, @NotNull String userName) {
		this.chat_uid = chat_uid;
		this.userName = userName;
	}

	public @NotNull String getUserName() {
		return userName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ChatUser chatUser = (ChatUser) o;
		return chat_uid == chatUser.chat_uid;
	}

	@Override
	public int hashCode() {
		return chat_uid;
	}

	@Override
	public String toString() {
		return "ChatUser{" +
			"chat_uid=" + chat_uid +
			", userName='" + userName + '\'' +
			'}';
	}
}
