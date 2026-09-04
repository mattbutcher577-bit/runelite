package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeTradeStateMachineCollectTest
{
	@Test
	public void testPartialCancelledBuyCollectsActualFillAndCreatesSell()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot market = new GeMarketSnapshot(now, Collections.singletonList(
			new GeMarketItem(1127, "Adamant platebody", false, 125, 9001, 9321, 500)));
		GeLimitLedger limits = new GeLimitLedger();
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = new GeTradeStateMachine(
			config(), limits, trades, () -> market, () -> true, () -> false);

		driveBuyToPlaced(machine, now);

		GePlannedAction open = machine.onTick(state(
			slot("CANCELLED_BUY", 1127, 125, 73, 9001), GePromptMode.NONE,
			-1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(10));
		assertEquals(GePlannedActionType.OPEN_OFFER, open.getType());

		GePlannedAction collect = machine.onTick(state(
			slot("CANCELLED_BUY", 1127, 125, 73, 9001), GePromptMode.NONE,
			-1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(11));
		assertEquals(GePlannedActionType.COLLECT, collect.getType());

		Map<Integer, Integer> inventory = new HashMap<>();
		inventory.put(1127, 73);
		GePlannedAction sell = machine.onTick(state(
			slot("EMPTY", -1, 0, 0, 0), GePromptMode.NONE,
			-1, 0, 0, GeTradeSide.UNKNOWN, inventory, 2_035_687L), now.plusSeconds(12));
		assertEquals(GePlannedActionType.OPEN_SELL, sell.getType());
		assertEquals(73, sell.getQuantity());
		assertEquals(9321, sell.getPrice());
		assertEquals(52, limits.remaining(1127, 125, now.plusSeconds(12)));
		assertEquals(1, trades.all().size());
		GeTradeObligation obligation = trades.all().iterator().next();
		assertEquals(GeTradeSide.SELL, obligation.getSide());
		assertEquals(73, obligation.getIntendedQuantity());
		assertEquals(9321, obligation.getIntendedPrice());
	}

	static void driveBuyToPlaced(GeTradeStateMachine machine, Instant now)
	{
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.NONE,
			-1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now);
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.ITEM_SEARCH,
			-1, 0, 0, GeTradeSide.BUY, Collections.emptyMap(), 2_035_687L), now.plusSeconds(1));
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.NONE,
			-1, 0, 0, GeTradeSide.BUY, Collections.emptyMap(), 2_035_687L), now.plusSeconds(2));
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.NONE,
			1127, 0, 0, GeTradeSide.BUY, Collections.emptyMap(), 2_035_687L), now.plusSeconds(3));
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.QUANTITY,
			1127, 0, 0, GeTradeSide.BUY, Collections.emptyMap(), 2_035_687L), now.plusSeconds(4));
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.NONE,
			1127, 125, 0, GeTradeSide.BUY, Collections.emptyMap(), 2_035_687L), now.plusSeconds(5));
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.PRICE,
			1127, 125, 0, GeTradeSide.BUY, Collections.emptyMap(), 2_035_687L), now.plusSeconds(6));
		machine.onTick(state(slot("EMPTY", -1, 0, 0, 0), GePromptMode.NONE,
			1127, 125, 9001, GeTradeSide.BUY, Collections.emptyMap(), 2_035_687L), now.plusSeconds(7));
		machine.onTick(state(slot("BUYING", 1127, 125, 73, 9001), GePromptMode.NONE,
			-1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(8));
	}

	static GeObservedSlot slot(String state, int itemId, int total, int filled, int price)
	{
		return new GeObservedSlot(1, state, itemId, total, filled, price);
	}

	static GeObservedState state(
		GeObservedSlot slot1,
		GePromptMode prompt,
		int setupItemId,
		int setupQuantity,
		int setupPrice,
		GeTradeSide side,
		Map<Integer, Integer> inventory,
		long gp)
	{
		return new GeObservedState(
			true, false, true, true, false, 301, gp,
			Arrays.asList(slot1,
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			inventory, setupItemId, setupQuantity, setupPrice, side, prompt);
	}

	static GeAutoTraderConfig config()
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
