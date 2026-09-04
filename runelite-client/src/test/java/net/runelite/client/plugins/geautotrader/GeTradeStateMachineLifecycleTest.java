package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeTradeStateMachineLifecycleTest
{
	@Test
	public void testFullBuyCollectSellCollectLifecycleReturnsSlotToIdle()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		AtomicReference<GeMarketSnapshot> market = new AtomicReference<>(new GeMarketSnapshot(
			now, Collections.singletonList(new GeMarketItem(1982, "Tomato", false, 1000, 160, 180, 500))));
		GeLimitLedger limits = new GeLimitLedger();
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = new GeTradeStateMachine(
			config(), limits, trades, market::get, () -> true, () -> false);

		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now, GePlannedActionType.OPEN_BUY);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.ITEM_SEARCH, -1, 0, 0, GeTradeSide.BUY), now.plusSeconds(1), GePlannedActionType.TYPE_ITEM_SEARCH);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.BUY), now.plusSeconds(2), GePlannedActionType.SELECT_ITEM);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.NONE, 1982, 0, 0, GeTradeSide.BUY), now.plusSeconds(3), GePlannedActionType.OPEN_QUANTITY);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.QUANTITY, 1982, 0, 0, GeTradeSide.BUY), now.plusSeconds(4), GePlannedActionType.TYPE_QUANTITY);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.NONE, 1982, 1000, 0, GeTradeSide.BUY), now.plusSeconds(5), GePlannedActionType.OPEN_PRICE);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.PRICE, 1982, 1000, 0, GeTradeSide.BUY), now.plusSeconds(6), GePlannedActionType.TYPE_PRICE);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_000_000L, inventory(), GePromptMode.NONE, 1982, 1000, 160, GeTradeSide.BUY), now.plusSeconds(7), GePlannedActionType.CONFIRM);

		assertAction(machine, observed("BUYING", 1982, 1000, 0, 160, 1_840_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(8), GePlannedActionType.NONE);
		assertEquals(GeTradePhase.MONITOR_BUY, machine.getPhase(1));
		assertAction(machine, observed("BOUGHT", 1982, 1000, 1000, 160, 1_840_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(9), GePlannedActionType.OPEN_OFFER);
		assertAction(machine, observed("BOUGHT", 1982, 1000, 1000, 160, 1_840_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(10), GePlannedActionType.COLLECT);

		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 1_840_000L, inventory(1982, 1000), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(11), GePlannedActionType.OPEN_SELL);
		assertEquals(0, limits.remaining(1982, 1000, now.plusSeconds(11)));
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 1_840_000L, inventory(1982, 1000), GePromptMode.NONE, -1, 0, 0, GeTradeSide.SELL), now.plusSeconds(12), GePlannedActionType.SELECT_SELL_ITEM);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 1_840_000L, inventory(1982, 1000), GePromptMode.NONE, 1982, 0, 0, GeTradeSide.SELL), now.plusSeconds(13), GePlannedActionType.OPEN_QUANTITY);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 1_840_000L, inventory(1982, 1000), GePromptMode.QUANTITY, 1982, 0, 0, GeTradeSide.SELL), now.plusSeconds(14), GePlannedActionType.TYPE_QUANTITY);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 1_840_000L, inventory(1982, 1000), GePromptMode.NONE, 1982, 1000, 0, GeTradeSide.SELL), now.plusSeconds(15), GePlannedActionType.OPEN_PRICE);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 1_840_000L, inventory(1982, 1000), GePromptMode.PRICE, 1982, 1000, 0, GeTradeSide.SELL), now.plusSeconds(16), GePlannedActionType.TYPE_PRICE);
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 1_840_000L, inventory(1982, 1000), GePromptMode.NONE, 1982, 1000, 180, GeTradeSide.SELL), now.plusSeconds(17), GePlannedActionType.CONFIRM);

		assertAction(machine, observed("SELLING", 1982, 1000, 0, 180, 1_840_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(18), GePlannedActionType.NONE);
		assertEquals(GeTradePhase.MONITOR_SELL, machine.getPhase(1));
		assertAction(machine, observed("SOLD", 1982, 1000, 1000, 180, 1_840_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(19), GePlannedActionType.OPEN_OFFER);
		assertAction(machine, observed("SOLD", 1982, 1000, 1000, 180, 1_840_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(20), GePlannedActionType.COLLECT);

		market.set(new GeMarketSnapshot(now.plusSeconds(21), Collections.emptyList()));
		assertAction(machine, observed("EMPTY", -1, 0, 0, 0, 2_020_000L, inventory(), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(21), GePlannedActionType.NONE);
		assertEquals(GeTradePhase.IDLE, machine.getPhase(1));
		assertTrue(trades.all().isEmpty());
	}

	private static void assertAction(
		GeTradeStateMachine machine,
		GeObservedState state,
		Instant at,
		GePlannedActionType expected)
	{
		assertEquals(expected, machine.onTick(state, at).getType());
	}

	private static GeObservedState observed(
		String state,
		int itemId,
		int total,
		int filled,
		int price,
		long gp,
		Map<Integer, Integer> inventory,
		GePromptMode prompt,
		int setupItemId,
		int setupQuantity,
		int setupPrice,
		GeTradeSide side)
	{
		return new GeObservedState(
			true, false, true, true, false, 455, gp,
			Arrays.asList(
				new GeObservedSlot(1, state, itemId, total, filled, price),
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			inventory, setupItemId, setupQuantity, setupPrice, side, prompt);
	}

	private static Map<Integer, Integer> inventory()
	{
		return Collections.emptyMap();
	}

	private static Map<Integer, Integer> inventory(int itemId, int quantity)
	{
		Map<Integer, Integer> inventory = new HashMap<>();
		inventory.put(itemId, quantity);
		return inventory;
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
