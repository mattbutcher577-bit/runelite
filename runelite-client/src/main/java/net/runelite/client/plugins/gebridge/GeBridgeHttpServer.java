package net.runelite.client.plugins.gebridge;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

final class GeBridgeHttpServer
{
	private static final String HOST = "127.0.0.1";

	private final Gson gson;
	private final Supplier<GeBridgeSnapshot> snapshotSupplier;
	private final int configuredPort;
	private HttpServer server;

	GeBridgeHttpServer(Gson gson, Supplier<GeBridgeSnapshot> snapshotSupplier, int configuredPort)
	{
		this.gson = gson;
		this.snapshotSupplier = snapshotSupplier;
		this.configuredPort = configuredPort;
	}

	void start() throws IOException
	{
		if (server != null)
		{
			return;
		}

		InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(HOST), configuredPort);
		server = HttpServer.create(address, 0);
		server.createContext("/state", this::handleState);
		server.setExecutor(null);
		server.start();
	}

	void stop()
	{
		if (server != null)
		{
			server.stop(0);
			server = null;
		}
	}

	int getPort()
	{
		if (server == null)
		{
			return configuredPort;
		}
		return server.getAddress().getPort();
	}

	private void handleState(HttpExchange exchange) throws IOException
	{
		try
		{
			if (!"GET".equals(exchange.getRequestMethod()))
			{
				exchange.getResponseHeaders().set("Allow", "GET");
				send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
				return;
			}

			GeBridgeSnapshot snapshot = snapshotSupplier.get();
			if (snapshot == null)
			{
				send(exchange, 503, "{\"error\":\"state_unavailable\"}");
				return;
			}

			send(exchange, 200, gson.toJson(snapshot));
		}
		finally
		{
			exchange.close();
		}
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream output = exchange.getResponseBody())
		{
			output.write(bytes);
		}
	}
}
