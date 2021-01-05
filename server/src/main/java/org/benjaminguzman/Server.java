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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import javax.management.InstanceAlreadyExistsException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {
	public static Logger logger;
	public static void main(String... args) {
		CommandLineParser cliParser = new DefaultParser();
		CommandLine cli;

		logger = LogManager.getLogger(Server.class);

		Options opts = new Options()
			.addOption("p", "port", true, "Server binding port")
			.addOption("a", "address", true, "Server binding address");

		// defaults
		int port = 12365;
		String ipStr = "127.0.0.1";
		try {
			cli = cliParser.parse(opts, args);
			port = Integer.parseInt(cli.getOptionValue('p', String.valueOf(port)));
			ipStr = cli.getOptionValue('a', ipStr);
		} catch (ParseException e) {
			System.out.println("Unrecognized option(s): " + Arrays.toString(args));
			HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.printHelp("Server", opts);
		}

		ChatServer server;
		try {
			server = new ChatServer(port, InetAddress.getByName(ipStr));
		} catch (UnknownHostException e) {
			logger.fatal("Error while binding server", e);
			return;
		}
		try {
			server.init();
			logger.debug("Server is listening @ {}:{}", ipStr, port);
		} catch (IOException e) {
			logger.fatal("Error while starting socket!", e);
			return;
		}

		server.newListener(); // run a thread to accept incoming connections
	}
}
