package net.runelite.client.plugins.gebridge;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeHttpServerTest
{
	@Test
	public void testStateEndpointReturnsJsonAndRejectsWrites() throws Exception
	{
		GeBridgeSnapshot snapshot = new GeBridgeSnapshot(
			1,
			123L,
			"LOGGED_IN",
			Collections.emptyList(),
			Collections.emptyList(),
			0
		);
		AtomicReference<GeBridgeSnapshot> ref = new AtomicReference<>(snapshot);
		GeBridgeHttpServer server = new GeBridgeHttpServer(new Gson(), ref::get, 0);
		server.start();
		try
		{
			int port = server.getPort();
			URL url = new URL("http://127.0.0.1:" + port + "/state");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			assertEquals(200, connection.getResponseCode());
			String body;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
			{
				body = reader.readLine();
			}
			assertTrue(body.contains("\"protocol\":1"));
			assertTrue(body.contains("\"gameState\":\"LOGGED_IN\""));

			HttpURLConnection post = (HttpURLConnection) url.openConnection();
			post.setRequestMethod("POST");
			post.setDoOutput(true);
			assertEquals(405, post.getResponseCode());
		}
		finally
		{
			server.stop();
		}
	}
}
