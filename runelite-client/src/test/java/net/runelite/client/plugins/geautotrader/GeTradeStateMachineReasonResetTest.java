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
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		trades.reserveBuy("v6-buy-1", 1, 1511, "Oak logs", 1000, 14, 15);
		trades.reserveBuy("v6-buy-2", 2, 333, "Trout", 1000, 18, 19);
		trades.reserveBuy("v6-buy-3", 3, 950, "Silk", 1000, 20, 21);

		GeTradeStateMachine machine = new GeTradeStateMachine(
			config(),
			new GeLimitLedger(),
			trades,
			() -> new GeMarketSnapshot(now, Collections.emptyList()),
			() -> true,
			() -> false);

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
				new GeObservedSlot(3, "BUYING", 950, 1000, 0, 20)),
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
