package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class GeTradeStateMachineRestartRecoveryTest
{
	@Test
	public void testOwnedCompletedBuyIsResumedAndOpenedForCollection()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		trades.reserveBuy("v6-buy-7", 1, 1982, "Tomato", 1000, 160);
		trades.markPlaced("v6-buy-7", now.minusSeconds(30));
		trades.markFilled("v6-buy-7", 1000);

		GeTradeStateMachine machine = machine(trades, now);
		GePlannedAction action = machine.onTick(observed("BOUGHT", 1982, 1000, 1000, 160), now);

		assertEquals(GePlannedActionType.OPEN_OFFER, action.getType());
		assertEquals(GeTradePhase.WAIT_BUY_COLLECT_READY, machine.getPhase(1));
	}

	@Test
	public void testOwnedCompletedSellIsResumedAndOpenedForCollection()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		trades.createSell("v6-sell-8", "v6-buy-7", 1, 1982, "Tomato", 1000, 180);
		trades.markPlaced("v6-sell-8", now.minusSeconds(30));
		trades.markFilled("v6-sell-8", 1000);

		GeTradeStateMachine machine = machine(trades, now);
		GePlannedAction action = machine.onTick(observed("SOLD", 1982, 1000, 1000, 180), now);

		assertEquals(GePlannedActionType.OPEN_OFFER, action.getType());
		assertEquals(GeTradePhase.WAIT_SELL_COLLECT_READY, machine.getPhase(1));
	}

	@Test
	public void testUnownedCompletedBuyIsAdoptedAndOpenedForCollection()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = machine(trades, now);

		GePlannedAction action = machine.onTick(observed("BOUGHT", 1982, 1000, 1000, 160), now);

		assertEquals(GePlannedActionType.OPEN_OFFER, action.getType());
		assertEquals(GeTradePhase.WAIT_BUY_COLLECT_READY, machine.getPhase(1));
		GeTradeObligation adopted = trades.findBySlot(1);
		assertNotNull(adopted);
		assertEquals(GeTradeSide.BUY, adopted.getSide());
		assertEquals(180, adopted.getTargetSellPrice());
	}

	@Test
	public void testUnownedActiveBuyIsAdoptedAndMonitored()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = machine(trades, now);

		GePlannedAction action = machine.onTick(observed("BUYING", 1982, 1000, 125, 160), now);

		assertEquals(GePlannedActionType.NONE, action.getType());
		assertEquals(GeTradePhase.MONITOR_BUY, machine.getPhase(1));
		GeTradeObligation adopted = trades.findBySlot(1);
		assertNotNull(adopted);
		assertEquals(125, adopted.getFilledQuantity());
	}

	@Test
	public void testUnownedCompletedSellIsAdoptedAndOpenedForCollection()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = machine(trades, now);

		GePlannedAction action = machine.onTick(observed("SOLD", 1982, 1000, 1000, 180), now);

		assertEquals(GePlannedActionType.OPEN_OFFER, action.getType());
		assertEquals(GeTradePhase.WAIT_SELL_COLLECT_READY, machine.getPhase(1));
		GeTradeObligation adopted = trades.findBySlot(1);
		assertNotNull(adopted);
		assertEquals(GeTradeSide.SELL, adopted.getSide());
	}

	@Test
	public void testUnownedActiveSellIsAdoptedAndMonitored()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = machine(trades, now);

		GePlannedAction action = machine.onTick(observed("SELLING", 1982, 1000, 250, 180), now);

		assertEquals(GePlannedActionType.NONE, action.getType());
		assertEquals(GeTradePhase.MONITOR_SELL, machine.getPhase(1));
		GeTradeObligation adopted = trades.findBySlot(1);
		assertNotNull(adopted);
		assertEquals(250, adopted.getFilledQuantity());
	}

	@Test
	public void testUnownedCancelledBuyWithNoFillCollectsRefundAndReturnsIdle()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = machine(trades, now);

		assertEquals(
			GePlannedActionType.OPEN_OFFER,
			machine.onTick(observed("CANCELLED_BUY", 1982, 1000, 0, 160, 1_840_000L, Collections.emptyMap()), now).getType());
		assertEquals(
			GePlannedActionType.COLLECT,
			machine.onTick(observed("CANCELLED_BUY", 1982, 1000, 0, 160, 1_840_000L, Collections.emptyMap()), now.plusSeconds(1)).getType());
		assertEquals(
			GePlannedActionType.NONE,
			machine.onTick(observed("EMPTY", -1, 0, 0, 0, 2_000_000L, Collections.emptyMap()), now.plusSeconds(2)).getType());

		assertEquals(GeTradePhase.IDLE, machine.getPhase(1));
		assertNull(trades.findBySlot(1));
	}

	private static GeTradeStateMachine machine(GeTradeLedger trades, Instant now)
	{
		GeMarketSnapshot market = new GeMarketSnapshot(
			now,
			Collections.singletonList(new GeMarketItem(1982, "Tomato", false, 1000, 160, 180, 500)));
		return new GeTradeStateMachine(
			config(), new GeLimitLedger(), trades, () -> market, () -> true, () -> false);
	}

	private static GeObservedState observed(String offerState, int itemId, int total, int filled, int price)
	{
		return observed(offerState, itemId, total, filled, price, 2_000_000L, Collections.emptyMap());
	}

	private static GeObservedState observed(
		String offerState,
		int itemId,
		int total,
		int filled,
		int price,
		long gp,
		Map<Integer, Integer> inventory)
	{
		return new GeObservedState(
			true, false, true, true, false, 453, gp,
			Arrays.asList(
				new GeObservedSlot(1, offerState, itemId, total, filled, price),
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			inventory, -1, 0, 0, GeTradeSide.UNKNOWN, GePromptMode.NONE);
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
