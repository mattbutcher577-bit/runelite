package net.runelite.client.plugins.gebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeLoginSettlePolicyTest
{
	@Test
	public void testTwoFreshGameTicksAreRequiredAfterLogin()
	{
		assertFalse(GeBridgeLoginSettlePolicy.isSettled(0));
		assertFalse(GeBridgeLoginSettlePolicy.isSettled(1));
		assertTrue(GeBridgeLoginSettlePolicy.isSettled(2));
		assertTrue(GeBridgeLoginSettlePolicy.isSettled(3));
	}
}
