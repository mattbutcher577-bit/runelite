package net.runelite.client.plugins.geautotrader;

import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeSafetyPolicyTest
{
	@Test
	public void testMembersWorldFailsClosed()
	{
		GeObservedState state = state(true, true, true, true, false);
		assertEquals(GeReasonCode.WORLD_NOT_F2P,
			GeSafetyPolicy.evaluateGlobal(state, true, false));
	}

	@Test
	public void testOnlySlotsOneToThreeAreOwned()
	{
		assertTrue(GeSafetyPolicy.canUseSlot(1));
		assertTrue(GeSafetyPolicy.canUseSlot(3));
		assertFalse(GeSafetyPolicy.canUseSlot(4));
	}

	@Test
	public void testStoppedAlwaysWins()
	{
		GeObservedState state = state(true, false, true, true, false);
		assertEquals(GeReasonCode.STOPPED_F8,
			GeSafetyPolicy.evaluateGlobal(state, true, true));
	}

	@Test
	public void testDisabledBeforeNormalTrading()
	{
		GeObservedState state = state(true, false, true, true, false);
		assertEquals(GeReasonCode.DISABLED,
			GeSafetyPolicy.evaluateGlobal(state, false, false));
	}

	private static GeObservedState state(
		boolean loggedIn,
		boolean membersWorld,
		boolean loginSettled,
		boolean geOpen,
		boolean blocker)
	{
		return new GeObservedState(
			loggedIn,
			membersWorld,
			loginSettled,
			geOpen,
			blocker,
			2_000_000L,
			Collections.emptyList(),
			-1,
			0,
			0,
			GeTradeSide.UNKNOWN);
	}
}
