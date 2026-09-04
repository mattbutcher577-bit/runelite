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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeHttpServerTest
{
	@Test
	public void testStateEndpointReturnsProtocolV3JsonAndRejectsWrites() throws Exception
	{
		GeBridgeSnapshot snapshot = new GeBridgeSnapshot(
			3,
			123L,
			42L,
			"LOGGED_IN",
			Collections.emptyList(),
			Collections.emptyList(),
			0,
			new GeBridgeClientState(
				true, 301, Collections.emptyList(), false, 773, 535, 765, 503, 4, 4, 548, 50),
			new GeBridgePlayerState(true, 3164, 3487, 0),
			new GeBridgeInterfaceState(true, false, false, false, false, false, false),
			new GeBridgeGeState(
				true,
				false,
				-1,
				new GeBridgeBounds(20, 20, 500, 360, true),
				GeBridgeBounds.invalid(),
				new GeBridgeBounds(550, 200, 180, 250, true)
			),
			new GeBridgeInventoryState(28, 1, 27),
			new GeBridgeSafetyState(true, false, true, true),
			new GeBridgeInputState(
				120L, 110L, 120L, 115L, 118L, 100L, 90L,
				400, 250, true, 0, 1, -1, "SHIFT", 3L)
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
			assertTrue(body.contains("\"protocol\":3"));
			assertTrue(body.contains("\"gameState\":\"LOGGED_IN\""));
			assertTrue(body.contains("\"canvasWidth\":773"));
			assertTrue(body.contains("\"safeForGeMouseActions\":true"));
			assertTrue(body.contains("\"mouseX\":400"));
			assertTrue(body.contains("\"lastControlKey\":\"SHIFT\""));
			assertFalse(body.contains("typedText"));
			assertFalse(body.contains("keyChar"));

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
