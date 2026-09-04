package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeTradeStateMachineSellTest
{
	@Test
	public void testSellLifecycleUsesFixedInitialSellPriceAndCollectsGp()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot market = new GeMarketSnapshot(now, Collections.singletonList(
			new GeMarketItem(1127, "Adamant platebody", false, 125, 9001, 9321, 500)));
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = new GeTradeStateMachine(
			GeTradeStateMachineCollectTest.config(), new GeLimitLedger(), trades,
			() -> market, () -> true, () -> false);

		GeTradeStateMachineCollectTest.driveBuyToPlaced(machine, now);
		machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("CANCELLED_BUY", 1127, 125, 73, 9001),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(10));
		machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("CANCELLED_BUY", 1127, 125, 73, 9001),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(11));
		Map<Integer, Integer> inventory = new HashMap<>();
		inventory.put(1127, 73);
		GePlannedAction action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, inventory, 2_035_687L), now.plusSeconds(12));
		assertEquals(GePlannedActionType.OPEN_SELL, action.getType());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.SELL, inventory, 2_035_687L), now.plusSeconds(13));
		assertEquals(GePlannedActionType.SELECT_SELL_ITEM, action.getType());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.NONE, 1127, 0, 0, GeTradeSide.SELL, inventory, 2_035_687L), now.plusSeconds(14));
		assertEquals(GePlannedActionType.OPEN_QUANTITY, action.getType());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.QUANTITY, 1127, 0, 0, GeTradeSide.SELL, inventory, 2_035_687L), now.plusSeconds(15));
		assertEquals(GePlannedActionType.TYPE_QUANTITY, action.getType());
		assertEquals(73, action.getQuantity());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.NONE, 1127, 73, 0, GeTradeSide.SELL, inventory, 2_035_687L), now.plusSeconds(16));
		assertEquals(GePlannedActionType.OPEN_PRICE, action.getType());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.PRICE, 1127, 73, 0, GeTradeSide.SELL, inventory, 2_035_687L), now.plusSeconds(17));
		assertEquals(GePlannedActionType.TYPE_PRICE, action.getType());
		assertEquals(9321, action.getPrice());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.NONE, 1127, 73, 9321, GeTradeSide.SELL, inventory, 2_035_687L), now.plusSeconds(18));
		assertEquals(GePlannedActionType.CONFIRM, action.getType());

		GePlannedAction duplicate = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.NONE, 1127, 73, 9321, GeTradeSide.SELL, inventory, 2_035_687L), now.plusSeconds(19));
		assertEquals(GePlannedActionType.NONE, duplicate.getType());

		machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("SELLING", 1127, 73, 0, 9321),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(20));

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("SOLD", 1127, 73, 73, 9321),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(21));
		assertEquals(GePlannedActionType.OPEN_OFFER, action.getType());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("SOLD", 1127, 73, 73, 9321),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L), now.plusSeconds(22));
		assertEquals(GePlannedActionType.COLLECT, action.getType());

		action = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("EMPTY", -1, 0, 0, 0),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_700_000L), now.plusSeconds(23));
		assertEquals(GeTradePhase.IDLE, machine.getPhase(1));
		assertEquals(GePlannedActionType.OPEN_BUY, action.getType());
		assertEquals(2, action.getSlot());
	}
}
