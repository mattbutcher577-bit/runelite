package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class GeTradeStateMachineBuyTest
{
	@Test
	public void testBuyLifecycleEmitsOneActionPerVerifiedPhase()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot market = new GeMarketSnapshot(now, Collections.singletonList(
			new GeMarketItem(1127, "Adamant platebody", false, 125, 9001, 9321, 500)));
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = new GeTradeStateMachine(
			config(), new GeLimitLedger(), trades, () -> market, () -> true, () -> false);

		GePlannedAction action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now);
		assertEquals(GePlannedActionType.OPEN_BUY, action.getType());
		assertEquals(1, action.getSlot());

		action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.ITEM_SEARCH, -1, 0, 0, GeTradeSide.BUY), now.plusSeconds(1));
		assertEquals(GePlannedActionType.TYPE_ITEM_SEARCH, action.getType());

		action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.NONE, -1, 0, 0, GeTradeSide.BUY), now.plusSeconds(2));
		assertEquals(GePlannedActionType.SELECT_ITEM, action.getType());
		assertEquals(1127, action.getItemId());

		action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.NONE, 1127, 0, 0, GeTradeSide.BUY), now.plusSeconds(3));
		assertEquals(GePlannedActionType.OPEN_QUANTITY, action.getType());

		action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.QUANTITY, 1127, 0, 0, GeTradeSide.BUY), now.plusSeconds(4));
		assertEquals(GePlannedActionType.TYPE_QUANTITY, action.getType());
		assertEquals(125, action.getQuantity());

		action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.NONE, 1127, 125, 0, GeTradeSide.BUY), now.plusSeconds(5));
		assertEquals(GePlannedActionType.OPEN_PRICE, action.getType());

		action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.PRICE, 1127, 125, 0, GeTradeSide.BUY), now.plusSeconds(6));
		assertEquals(GePlannedActionType.TYPE_PRICE, action.getType());
		assertEquals(9001, action.getPrice());

		action = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.NONE, 1127, 125, 9001, GeTradeSide.BUY), now.plusSeconds(7));
		assertEquals(GePlannedActionType.CONFIRM, action.getType());

		GePlannedAction duplicate = machine.onTick(state(
			slot(1, "EMPTY", -1, 0, 0, 0), GePromptMode.NONE, 1127, 125, 9001, GeTradeSide.BUY), now.plusSeconds(8));
		assertEquals(GePlannedActionType.NONE, duplicate.getType());

		machine.onTick(state(
			slot(1, "BUYING", 1127, 125, 0, 9001), GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN), now.plusSeconds(9));
		GeTradeObligation obligation = trades.all().iterator().next();
		assertNotNull(obligation.getPlacedAt());
	}

	private static GeObservedSlot slot(int slot, String state, int itemId, int total, int filled, int price)
	{
		return new GeObservedSlot(slot, state, itemId, total, filled, price);
	}

	private static GeObservedState state(
		GeObservedSlot slot1,
		GePromptMode prompt,
		int setupItemId,
		int setupQuantity,
		int setupPrice,
		GeTradeSide side)
	{
		return new GeObservedState(
			true, false, true, true, false, 301, 2_035_687L,
			Arrays.asList(slot1, slot(2, "EMPTY", -1, 0, 0, 0), slot(3, "EMPTY", -1, 0, 0, 0)),
			Collections.emptyMap(), setupItemId, setupQuantity, setupPrice, side, prompt);
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
