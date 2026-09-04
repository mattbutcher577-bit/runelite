package net.runelite.client.plugins.geautotrader;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeTradeSchedulerTest
{
	@Test
	public void testBuyDoesNotAbortBeforeTwentyMinutesAndAbortsOnceAfter()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot market = new GeMarketSnapshot(now, Collections.singletonList(
			new GeMarketItem(1127, "Adamant platebody", false, 125, 9001, 9321, 500)));
		GeTradeLedger trades = new GeTradeLedger();
		GeTradeStateMachine machine = new GeTradeStateMachine(
			GeTradeStateMachineCollectTest.config(), new GeLimitLedger(), trades,
			() -> market, () -> true, () -> false);
		GeTradeStateMachineCollectTest.driveBuyToPlaced(machine, now);
		Instant placed = trades.all().iterator().next().getPlacedAt();

		GePlannedAction before = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("BUYING", 1127, 125, 50, 9001),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L),
			placed.plus(Duration.ofMinutes(20)).minusSeconds(1));
		assertEquals(GePlannedActionType.NONE, before.getType());

		GePlannedAction open = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("BUYING", 1127, 125, 50, 9001),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L),
			placed.plus(Duration.ofMinutes(20)));
		assertEquals(GePlannedActionType.OPEN_OFFER, open.getType());

		GePlannedAction abort = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("BUYING", 1127, 125, 50, 9001),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L),
			placed.plus(Duration.ofMinutes(20)).plusSeconds(1));
		assertEquals(GePlannedActionType.ABORT_BUY, abort.getType());

		GePlannedAction wait = machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("BUYING", 1127, 125, 50, 9001),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L),
			placed.plus(Duration.ofMinutes(21)));
		assertEquals(GePlannedActionType.NONE, wait.getType());

		machine.onTick(GeTradeStateMachineCollectTest.state(
			GeTradeStateMachineCollectTest.slot("CANCELLED_BUY", 1127, 125, 50, 9001),
			GePromptMode.NONE, -1, 0, 0, GeTradeSide.UNKNOWN, Collections.emptyMap(), 2_035_687L),
			placed.plus(Duration.ofMinutes(21)).plusSeconds(1));
		GeTradeObligation obligation = trades.all().iterator().next();
		assertEquals(1, obligation.getAbortCount());
	}
}
