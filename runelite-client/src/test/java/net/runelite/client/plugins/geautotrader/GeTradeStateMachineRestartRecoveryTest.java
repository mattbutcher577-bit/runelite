package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
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
	public void testUnownedExistingOfferIsNotAdopted()
	{
		Instant now = Instant.parse("2026-09-05T10:00:00Z");
		GeTradeStateMachine machine = machine(new GeTradeLedger(), now);

		GePlannedAction action = machine.onTick(observed("BOUGHT", 1982, 1000, 1000, 160), now);

		assertEquals(GePlannedActionType.NONE, action.getType());
		assertEquals(GeTradePhase.IDLE, machine.getPhase(1));
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
		return new GeObservedState(
			true, false, true, true, false, 453, 2_000_000L,
			Arrays.asList(
				new GeObservedSlot(1, offerState, itemId, total, filled, price),
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
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
