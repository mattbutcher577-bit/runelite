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
	public void testStateEndpointReturnsProtocolV5JsonAndRejectsWrites() throws Exception
	{
		GeBridgeSearchResult result = new GeBridgeSearchResult(
			0,
			314,
			"Feather",
			new GeBridgeBounds(40, 410, 32, 32, true),
			new GeBridgeBounds(80, 410, 150, 24, true));
		GeBridgeSnapshot snapshot = new GeBridgeSnapshot(
			5,
			123L,
			42L,
			"bridge-test-instance",
			9L,
			"LOGGED_IN",
			Collections.emptyList(),
			Collections.emptyList(),
			0,
			new GeBridgeClientState(
				true, 301, Collections.emptyList(), false,
				773, 535, 104, 232, true,
				773, 535, 773, 535, false,
				765, 503, 4, 4, 548, 50),
			new GeBridgePlayerState(true, 3164, 3487, 0),
			new GeBridgeInterfaceState(true, true, false, false, false, true, false),
			new GeBridgeGeState(
				true,
				true,
				-1,
				new GeBridgeBounds(20, 20, 500, 360, true),
				new GeBridgeBounds(40, 80, 440, 280, true),
				new GeBridgeBounds(550, 200, 180, 250, true)
			),
			new GeBridgeInventoryState(28, 1, 27),
			new GeBridgeSafetyState(true, true, false, false),
			new GeBridgeInputState(
				120L, 110L, 120L, 115L, 118L, 100L, 90L,
				400, 250, true, 0, 1, -1, "SHIFT", 3L),
			new GeBridgeSearchState(true, 42L, Collections.singletonList(result)),
			GeBridgeMouseState.unavailable()
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
			assertTrue(body.contains("\"protocol\":5"));
			assertTrue(body.contains("\"bridgeInstanceId\":\"bridge-test-instance\""));
			assertTrue(body.contains("\"snapshotSeq\":9"));
			assertTrue(body.contains("\"gameState\":\"LOGGED_IN\""));
			assertTrue(body.contains("\"canvasScreenX\":104"));
			assertTrue(body.contains("\"canvasScreenY\":232"));
			assertTrue(body.contains("\"canvasScreenPositionValid\":true"));
			assertTrue(body.contains("\"realWidth\":773"));
			assertTrue(body.contains("\"search\":{"));
			assertTrue(body.contains("\"updatedTick\":42"));
			assertTrue(body.contains("\"itemId\":314"));
			assertTrue(body.contains("\"name\":\"Feather\""));
			assertFalse(body.contains("\"query\""));
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
