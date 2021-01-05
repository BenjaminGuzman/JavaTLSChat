package org.benjaminguzman.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.HashMap;
import javax.management.InstanceAlreadyExistsException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.benjaminguzman.ChatClient;
import org.benjaminguzman.ChatUser;
import org.benjaminguzman.ServerConf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CLIClient implements Runnable {
	@NotNull
	private ChatClient client;

	@NotNull
	private BufferedReader input;

	@NotNull
	private final Logger logger;

	@NotNull
	private final String username;

	private HashMap<Integer, ChatUser> connectedUsers; // maps id -> username
	private HashMap<String, Integer> connectedIDs; // maps username -> id

	public CLIClient(@NotNull ServerConf serverConf, @NotNull String username) throws UnknownHostException, InstanceAlreadyExistsException {
		this.client = new ChatClient.Builder()
			.serverConf(serverConf)
			.setUsername(username)
			.onMessage(this::onMessage)
			.onConnectionSuccess(this::onReady)
			.onConnectedUsersList(this::onConnectedUsersList)
			.createChatClient();

		this.input = new BufferedReader(new InputStreamReader(System.in));
		this.logger = LogManager.getLogger(CLIClient.class);
		this.username = username;
		this.connectedIDs = new HashMap<>();
	}

	public void start() throws InterruptedException {
		this.client.start(); // start the thread
		this.client.join(); // block till the thread ends
	}

	private void onReady() {
		// run on a new thread because calling nextline on sysin will block
		new Thread(this).start();
	}

	private void onConnectedUsersList(@NotNull HashMap<Integer, ChatUser> connectedUsers) {
		this.connectedUsers = connectedUsers;

		System.out.println();
		System.out.println("Connected users list: ");
		this.connectedUsers.values().stream().map(ChatUser::getUserName).forEach(System.out::println);

		this.connectedIDs.clear();
		this.connectedUsers.keySet().forEach(user_id -> {
			this.connectedIDs.put(
				this.connectedUsers.get(user_id).getUserName().replace(" ", ""),
				user_id
			);
		});

		this.printPrompt();
	}

	private void onMessage(int from_id, String message) {
		System.out.println("\033[96m" + this.connectedUsers.get(from_id).getUserName() + "\033[0m: " + message);
		this.printPrompt();
	}

	@Override
	public void run() {
		String nextLine;
		Integer to_id;
		try {
			this.client.getConnectedUsers();

			while (true) {
				nextLine = this.input.readLine();

				if (nextLine == null) {
					this.client.interrupt();
					return;
				}

				// /users is a command to reload and display all connected users
				if ("/users".equalsIgnoreCase(nextLine)) {
					this.client.getConnectedUsers();
					continue;
				} else if ("/logout".equalsIgnoreCase(nextLine)) {
					this.client.interrupt();
					return;
				}

				to_id = this.getChatUIDFromMessageRef(nextLine);
				if (to_id == null || to_id == -1) {
					this.printHelp();
					continue;
				}

				this.client.sendMessage(nextLine, to_id);
			}
		} catch (IOException e) {
			this.logger.fatal("Error while reading input", e);
		} catch (InterruptedException e) {
			System.err.println("It is not possible to send more messages");
		}
	}

	private Integer getChatUIDFromMessageRef(String message) {
		// get the @Username reference
		int ref_start_idx;
		if ((ref_start_idx = message.indexOf('@')) == -1)
			return -1;

		// get username
		String toUsername = message.substring(ref_start_idx + 1).split(" ", 2)[0];
		return this.connectedIDs.get(toUsername);
	}

	private void printPrompt() {
		System.out.print('[' + this.username + "]: ");
	}

	private void printHelp() {
		System.out.println("To send a message to a user use the '@' reference without any space, " +
					   "e. g. if you want to message yourself, use: @" + this.username.replace(" ", ""));
		this.printPrompt();
	}
}
