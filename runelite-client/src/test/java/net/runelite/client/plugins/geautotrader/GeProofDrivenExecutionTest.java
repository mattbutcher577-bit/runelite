package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeProofDrivenExecutionTest
{
	@Test
	public void testCompletedBuyReopensOfferUntilDetailsVisibleThenCollects()
	{
		Instant now = Instant.parse("2026-09-05T15:00:00Z");
		GeTradeLedger trades = completedBuyLedger(now);
		GeTradeStateMachine machine = machine(trades, now);

		assertEquals(GePlannedActionType.OPEN_OFFER,
			machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now).getType());
		assertEquals(GePlannedActionType.OPEN_OFFER,
			machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now.plusMillis(600)).getType());
		assertEquals(GePlannedActionType.COLLECT,
			machine.onTick(state("BOUGHT", true, 0, 2_003_062L), now.plusMillis(1200)).getType());
	}

	@Test
	public void testOverviewReturningBeforeCollectionProofReopensOffer()
	{
		Instant now = Instant.parse("2026-09-05T15:00:00Z");
		GeTradeStateMachine machine = machine(completedBuyLedger(now), now);

		machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now);
		machine.onTick(state("BOUGHT", true, 0, 2_003_062L), now.plusMillis(600));
		assertEquals(GePlannedActionType.OPEN_OFFER,
			machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now.plusMillis(1200)).getType());
	}

	@Test
	public void testBuyCollectionProofCreatesSellFromActualInventoryDelta()
	{
		Instant now = Instant.parse("2026-09-05T15:00:00Z");
		GeTradeStateMachine machine = machine(completedBuyLedger(now), now);

		machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now);
		machine.onTick(state("BOUGHT", true, 0, 2_003_062L), now.plusMillis(600));
		GePlannedAction sell = machine.onTick(state("EMPTY", false, 125, 2_003_062L), now.plusMillis(1200));
		assertEquals(GePlannedActionType.OPEN_SELL, sell.getType());
		assertEquals(125, sell.getQuantity());
		assertEquals(6, sell.getPrice());
	}

	@Test
	public void testTargetUnavailableIsRetryableInsideFiveSecondWindow()
	{
		Instant now = Instant.parse("2026-09-05T15:00:00Z");
		GeTradeStateMachine machine = machine(completedBuyLedger(now), now);
		GePlannedAction open = machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now);

		assertFalse(machine.recordExecutionResult(
			open, GeReasonCode.EXECUTION_TARGET_UNAVAILABLE, now.plusSeconds(1)));
		assertEquals(GePlannedActionType.OPEN_OFFER, machine.getPendingAction(1));
	}

	@Test
	public void testTargetUnavailableBecomesTerminalAfterFiveSecondWindow()
	{
		Instant now = Instant.parse("2026-09-05T15:00:00Z");
		GeTradeStateMachine machine = machine(completedBuyLedger(now), now);
		GePlannedAction open = machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now);
		machine.recordExecutionResult(open, GeReasonCode.EXECUTION_TARGET_UNAVAILABLE, now.plusSeconds(1));

		assertTrue(machine.recordExecutionResult(
			open, GeReasonCode.EXECUTION_TARGET_UNAVAILABLE, now.plusSeconds(6)));
	}

	@Test
	public void testExecutionRejectedRemainsImmediateTerminalFailure()
	{
		Instant now = Instant.parse("2026-09-05T15:00:00Z");
		GeTradeStateMachine machine = machine(completedBuyLedger(now), now);
		GePlannedAction open = machine.onTick(state("BOUGHT", false, 0, 2_003_062L), now);

		assertTrue(machine.recordExecutionResult(
			open, GeReasonCode.EXECUTION_REJECTED, now.plusMillis(1)));
	}

	private static GeTradeLedger completedBuyLedger(Instant now)
	{
		GeTradeLedger trades = new GeTradeLedger();
		trades.reserveBuy("v6-buy-proof", 1, 1207, "Steel dagger", 125, 5, 6);
		trades.markPlaced("v6-buy-proof", now.minusSeconds(30));
		trades.markFilled("v6-buy-proof", 125);
		return trades;
	}

	private static GeTradeStateMachine machine(GeTradeLedger trades, Instant now)
	{
		GeMarketSnapshot market = new GeMarketSnapshot(now, Collections.singletonList(
			new GeMarketItem(1207, "Steel dagger", false, 125, 5, 6, 500)));
		return new GeTradeStateMachine(
			config(), new GeLimitLedger(), trades, () -> market, () -> true, () -> false);
	}

	private static GeObservedState state(String slotState, boolean detailsVisible, int daggerInventory, long gp)
	{
		Map<Integer, Integer> inventory = new HashMap<>();
		if (daggerInventory > 0)
		{
			inventory.put(1207, daggerInventory);
		}
		return new GeObservedState(
			true, false, true, true, false, 452, gp,
			Arrays.asList(
				"EMPTY".equals(slotState)
					? new GeObservedSlot(1, "EMPTY", -1, 0, 0, 0)
					: new GeObservedSlot(1, slotState, 1207, 125, 125, 5),
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			inventory,
			-1, 0, 0, GeTradeSide.UNKNOWN, GePromptMode.NONE,
			Collections.emptySet(), detailsVisible);
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
