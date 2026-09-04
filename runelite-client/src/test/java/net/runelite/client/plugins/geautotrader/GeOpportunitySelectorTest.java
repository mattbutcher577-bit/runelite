package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeOpportunitySelectorTest
{
	@Test
	public void testCurrentTwoPercentTaxIsApplied()
	{
		GeMarketItem item = new GeMarketItem(1, "x", false, 100, 9000, 9321, 100);
		assertEquals(135, item.unitProfitAfterTax());
	}

	@Test
	public void testMembersItemsAreFiltered()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeMarketSnapshot snapshot = new GeMarketSnapshot(now, Arrays.asList(
			new GeMarketItem(1, "members", true, 100, 1000, 1200, 100),
			new GeMarketItem(2, "f2p", false, 100, 1000, 1200, 100)));
		List<GeCandidate> result = GeOpportunitySelector.select(
			snapshot, 100_000, new GeLimitLedger(), new GeTradeLedger(), config(), now);
		assertEquals(1, result.size());
		assertEquals(2, result.get(0).getItemId());
	}

	@Test
	public void testQuantityCappedByRemainingLimitAndCapital()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeLimitLedger limits = new GeLimitLedger();
		limits.recordFill(2, 25, now);
		GeMarketSnapshot snapshot = new GeMarketSnapshot(now, Arrays.asList(
			new GeMarketItem(2, "f2p", false, 125, 1000, 1200, 100)));
		List<GeCandidate> result = GeOpportunitySelector.select(
			snapshot, 100_000, limits, new GeTradeLedger(), config(), now);
		assertEquals(100, result.get(0).getQuantity());
	}

	@Test
	public void testDuplicateActiveItemIsFiltered()
	{
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		GeTradeLedger trades = new GeTradeLedger();
		trades.reserveBuy("a", 1, 2, "f2p", 1, 1000);
		GeMarketSnapshot snapshot = new GeMarketSnapshot(now, Arrays.asList(
			new GeMarketItem(2, "f2p", false, 125, 1000, 1200, 100)));
		assertTrue(GeOpportunitySelector.select(
			snapshot, 100_000, new GeLimitLedger(), trades, config(), now).isEmpty());
	}

	private static GeAutoTraderConfig config()
	{
		return new GeAutoTraderConfig()
		{
			@Override
			public int minRoiBasisPoints()
			{
				return 100;
			}

			@Override
			public int minFiveMinuteVolume()
			{
				return 10;
			}

			@Override
			public int maxUnitBuyPrice()
			{
				return 20_000_000;
			}

			@Override
			public int maxQuantityPerOffer()
			{
				return 1000;
			}
		};
	}
}
