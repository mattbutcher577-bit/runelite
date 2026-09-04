package net.runelite.client.plugins.gebridge;

import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeClientSessionStateTest
{
	@Test
	public void testClientStateCarriesRuneLiteTickAndLoginSettlingState()
	{
		GeBridgeClientState state = new GeBridgeClientState(
			true, 301, Collections.emptyList(), false,
			765, 503, 104, 232, true,
			765, 503, 765, 503, false,
			512, 334, 4, 4, 548, 50,
			1234, 1232, true);

		assertEquals(1234, state.getClientTick());
		assertEquals(1232, state.getLastLoginTick());
		assertTrue(state.isLoginSettled());
	}
}
