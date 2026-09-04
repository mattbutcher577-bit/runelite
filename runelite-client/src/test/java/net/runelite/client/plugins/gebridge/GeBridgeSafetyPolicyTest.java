package net.runelite.client.plugins.gebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeSafetyPolicyTest
{
	@Test
	public void testGeOwnedInputIsNotTreatedAsModalBlocker()
	{
		GeBridgeInterfaceState interfaces = new GeBridgeInterfaceState(
			true, true, false, false, false, true, false);
		GeBridgeSafetyState safety = GeBridgeSafetyPolicy.evaluate(true, interfaces);

		assertFalse(safety.isModalBlocker());
		assertTrue(safety.isSafeForMouseActions());
		assertTrue(safety.isSafeForGeMouseActions());
	}

	@Test
	public void testUnrelatedChatboxInputStillBlocksMouseActions()
	{
		GeBridgeInterfaceState interfaces = new GeBridgeInterfaceState(
			true, false, false, false, false, true, false);
		GeBridgeSafetyState safety = GeBridgeSafetyPolicy.evaluate(true, interfaces);

		assertTrue(safety.isModalBlocker());
		assertFalse(safety.isSafeForMouseActions());
		assertFalse(safety.isSafeForGeMouseActions());
	}

	@Test
	public void testOtherModalInterfacesStillBlockInsideGeSetup()
	{
		GeBridgeInterfaceState interfaces = new GeBridgeInterfaceState(
			true, true, true, false, false, true, false);
		GeBridgeSafetyState safety = GeBridgeSafetyPolicy.evaluate(true, interfaces);

		assertTrue(safety.isModalBlocker());
		assertFalse(safety.isSafeForGeMouseActions());
	}
}
