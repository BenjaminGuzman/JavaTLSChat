import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.benjaminguzman.ChatClient;
import org.benjaminguzman.ServerConf;
import org.benjaminguzman.cli.CLIClient;

public class Chat {
	public static Logger logger;
	public static void main(String... args) {
		CommandLineParser cliParser = new DefaultParser();
		CommandLine cli;

		logger = LogManager.getLogger(Chat.class);

		Options opts = new Options()
			.addOption("p", "port", true, "Server binding port")
			.addOption("a", "address", true, "Server binding address")
			.addOption("t", "tls", false, "Flag to tell if the server is using TLS");

		// defaults
		int port = 12365;
		String ipStr = "127.0.0.1";
		boolean use_tls = false;
		try {
			cli = cliParser.parse(opts, args);
			port = Integer.parseInt(cli.getOptionValue('p', String.valueOf(port)));
			ipStr = cli.getOptionValue('a', ipStr);
			use_tls = cli.hasOption('t');
		} catch (ParseException e) {
			System.out.println("Unrecognized option(s): " + Arrays.toString(args));
			HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.printHelp("Client", opts);
		}

		try {
			CLIClient client = new CLIClient(new ServerConf(ipStr, port, use_tls), "Test client");
			client.start();
		} catch (UnknownHostException | InstanceAlreadyExistsException | InterruptedException e) {
			logger.fatal("Error while trying to create the chat client", e);
			//return;
		}
	}
}
