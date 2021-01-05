package org.benjaminguzman;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.jetbrains.annotations.NotNull;

/**
 * Just a simple wrapper class for the configuration of the server
 */
public class ServerConf {
	@NotNull
	public final InetAddress address;
	public final int port;
	public final boolean use_tls;

	public ServerConf(@NotNull String address, int port, boolean use_tls) throws UnknownHostException {
		this(InetAddress.getByName(address), port, use_tls);
	}
	public ServerConf(@NotNull InetAddress address, int port, boolean use_tls) {
		this.address = address;
		this.port = port;
		this.use_tls = use_tls;
	}
}
