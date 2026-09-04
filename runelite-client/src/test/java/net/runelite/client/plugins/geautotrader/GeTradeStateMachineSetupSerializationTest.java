package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GeTradeStateMachineSetupSerializationTest
{
	@Test
	public void testSecondSlotDoesNotStartWhileFirstBuySetupIsPending()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot market = new GeMarketSnapshot(now, Arrays.asList(
			new GeMarketItem(1127, "Adamant platebody", false, 125, 9001, 9321, 500),
			new GeMarketItem(1143, "Mithril med helm", false, 125, 500, 550, 500)));
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = new GeTradeStateMachine(
			config(), new GeLimitLedger(), trades, () -> market, () -> true, () -> false);

		GeObservedState emptyGe = state(GePromptMode.NONE, GeTradeSide.UNKNOWN);

		GePlannedAction first = machine.onTick(emptyGe, now);
		assertEquals(GePlannedActionType.OPEN_BUY, first.getType());
		assertEquals(1, first.getSlot());
		assertEquals(GeTradePhase.WAIT_BUY_SETUP, machine.getPhase(1));

		GePlannedAction second = machine.onTick(emptyGe, now.plusSeconds(1));
		assertEquals(GePlannedActionType.NONE, second.getType());
		assertEquals(GeTradePhase.IDLE, machine.getPhase(2));
		assertEquals(GeTradePhase.IDLE, machine.getPhase(3));
	}

	private static GeObservedState state(GePromptMode prompt, GeTradeSide side)
	{
		return new GeObservedState(
			true, false, true, true, false, 453, 2_035_687L,
			Arrays.asList(
				new GeObservedSlot(1, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			Collections.emptyMap(), -1, 0, 0, side, prompt);
	}

	private static GeAutoTraderConfig config()
	{
		return new GeAutoTraderConfig()
		{
			@Override public int minRoiBasisPoints() { return 100; }
			@Override public int minFiveMinuteVolume() { return 10; }
			@Override public int maxUnitBuyPrice() { return 20_000_000; }
			@Override public int maxQuantityPerOffer() { return 1000; }
			@Override public int marketRefreshSeconds() { return 30; }
		};
	}
}
