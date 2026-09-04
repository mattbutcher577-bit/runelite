package net.runelite.client.plugins.geautotrader;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class GeTradeStateMachineProofTimeoutTest
{
	@Test
	public void testBuySetupProofWaitTimesOutWithoutRepeatingAction()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot market = new GeMarketSnapshot(now, Collections.singletonList(
			new GeMarketItem(1982, "Tomato", false, 1000, 160, 180, 500)));
		GeTradeStateMachine machine = new GeTradeStateMachine(
			config(), new GeLimitLedger(), new GeTradeLedger(), () -> market, () -> true, () -> false);
		GeObservedState empty = state(slot(1, "EMPTY", -1, 0, 0, 0));

		assertEquals(GePlannedActionType.OPEN_BUY, machine.onTick(empty, now).getType());
		assertEquals(GeTradePhase.WAIT_BUY_SETUP, machine.getPhase(1));

		GePlannedAction afterTimeout = machine.onTick(empty, now.plusSeconds(11));
		assertEquals(GePlannedActionType.NONE, afterTimeout.getType());
		assertEquals("UI_STATE_TIMEOUT", machine.getLastReason().name());
	}

	@Test
	public void testMonitorPhasesAreNotShortUiProofWaits() throws Exception
	{
		Method method = GeTradeStateMachine.class.getDeclaredMethod("isUiProofPhase", GeTradePhase.class);
		method.setAccessible(true);
		assertFalse((Boolean) method.invoke(null, GeTradePhase.MONITOR_BUY));
		assertFalse((Boolean) method.invoke(null, GeTradePhase.MONITOR_SELL));
	}

	private static GeObservedSlot slot(int slot, String state, int itemId, int total, int filled, int price)
	{
		return new GeObservedSlot(slot, state, itemId, total, filled, price);
	}

	private static GeObservedState state(GeObservedSlot slot1)
	{
		return new GeObservedState(
			true, false, true, true, false, 301, 2_000_000L,
			Arrays.asList(slot1, slot(2, "EMPTY", -1, 0, 0, 0), slot(3, "EMPTY", -1, 0, 0, 0)),
			Collections.emptyMap(), -1, 0, 0, GeTradeSide.UNKNOWN, GePromptMode.NONE);
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
