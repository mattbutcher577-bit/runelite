package net.runelite.client.plugins.geautotrader;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

	@Test
	public void testAbortAndCollectPhasesOwnSharedGeWorkflow() throws Exception
	{
		GeTradePhase[] shared = {
			GeTradePhase.WAIT_ABORT_READY,
			GeTradePhase.WAIT_ABORT_RESULT,
			GeTradePhase.WAIT_BUY_COLLECT_READY,
			GeTradePhase.WAIT_BUY_COLLECT_RESULT,
			GeTradePhase.WAIT_SELL_COLLECT_READY,
			GeTradePhase.WAIT_SELL_COLLECT_RESULT
		};
		for (GeTradePhase phase : shared)
		{
			assertTrue(phase + " must own the shared GE workflow", isSharedPhase(phase));
		}
	}

	@Test
	public void testPureMonitoringDoesNotOwnSharedGeWorkflow() throws Exception
	{
		assertFalse(isSharedPhase(GeTradePhase.MONITOR_BUY));
		assertFalse(isSharedPhase(GeTradePhase.MONITOR_SELL));
	}

	private static boolean isSharedPhase(GeTradePhase phase) throws Exception
	{
		Method method = GeTradeStateMachine.class.getDeclaredMethod("isSetupWorkflowPhase", GeTradePhase.class);
		method.setAccessible(true);
		return (Boolean) method.invoke(null, phase);
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
