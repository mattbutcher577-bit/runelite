package net.runelite.client.plugins.worldhopper;

import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class WorldHopperConfigTest
{
	@Test
	public void testWorldPingIsDisabledByDefault()
	{
		WorldHopperConfig config = new WorldHopperConfig() { };
		assertFalse(config.ping());
	}
}
