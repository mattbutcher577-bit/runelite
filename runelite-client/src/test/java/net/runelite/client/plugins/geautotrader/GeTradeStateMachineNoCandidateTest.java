package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeTradeStateMachineNoCandidateTest
{
	@Test
	public void testNoCandidateReasonIsVisible()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot market = new GeMarketSnapshot(now, Collections.singletonList(
			new GeMarketItem(1127, "Adamant platebody", false, 125, 9001, 9050, 500)));
		GeAutoTraderConfig config = new GeAutoTraderConfig()
		{
			@Override public int minRoiBasisPoints() { return 500; }
			@Override public int minFiveMinuteVolume() { return 10; }
			@Override public int maxUnitBuyPrice() { return 20_000_000; }
			@Override public int maxQuantityPerOffer() { return 1000; }
			@Override public int marketRefreshSeconds() { return 30; }
		};
		GeTradeStateMachine machine = new GeTradeStateMachine(
			config, new GeLimitLedger(), new GeTradeLedger(), () -> market, () -> true, () -> false);
		GeObservedState state = new GeObservedState(
			true, false, true, true, false, 301, 2_035_687L,
			Arrays.asList(
				new GeObservedSlot(1, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(2, "EMPTY", -1, 0, 0, 0),
				new GeObservedSlot(3, "EMPTY", -1, 0, 0, 0)),
			Collections.emptyMap(), -1, 0, 0, GeTradeSide.UNKNOWN, GePromptMode.NONE);

		assertEquals(GePlannedActionType.NONE, machine.onTick(state, now).getType());
		assertEquals(GeReasonCode.NO_CANDIDATES, machine.getLastReason());
	}
}
