package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GeTradeStateMachineReasonResetTest
{
	@Test
	public void testHealthySettledTickClearsStaleLoginResyncWhenOwnedSlotsAreOccupied()
	{
		GeTradeStateMachine machine = new GeTradeStateMachine(
			config(),
			new GeLimitLedger(),
			new GeTradeLedger(),
			() -> new GeMarketSnapshot(Instant.parse("2026-09-05T10:00:00Z"), Collections.emptyList()),
			() -> true,
			() -> false);

		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		machine.onTick(state(false), now);
		assertEquals(GeReasonCode.LOGIN_RESYNC, machine.getLastReason());

		machine.onTick(state(true), now.plusSeconds(2));
		assertEquals(GeReasonCode.OK, machine.getLastReason());
	}

	private static GeObservedState state(boolean settled)
	{
		return new GeObservedState(
			true,
			false,
			settled,
			true,
			false,
			452,
			2_000_000L,
			Arrays.asList(
				new GeObservedSlot(1, "BUYING", 1511, 1000, 0, 14),
				new GeObservedSlot(2, "BUYING", 333, 1000, 0, 18),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			Collections.emptyMap(),
			-1,
			0,
			0,
			GeTradeSide.UNKNOWN,
			GePromptMode.NONE);
	}

	private static GeAutoTraderConfig config()
	{
		return new GeAutoTraderConfig()
		{
			@Override public boolean enabled() { return true; }
			@Override public int marketRefreshSeconds() { return 30; }
		};
	}
}
