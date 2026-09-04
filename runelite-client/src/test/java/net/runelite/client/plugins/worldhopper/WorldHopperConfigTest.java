/*
 * Copyright (c) 2026
 * All rights reserved.
 */
package net.runelite.client.plugins.worldhopper;

import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class WorldHopperConfigTest
{
	@Test
	public void defaultConfigurationDoesNotPingWorlds()
	{
		WorldHopperConfig config = new WorldHopperConfig() { };
		assertFalse(config.ping());
	}
}
