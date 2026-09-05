package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GeOpportunitySelector
{
	private GeOpportunitySelector()
	{
	}

	public static List<GeCandidate> select(
		GeMarketSnapshot snapshot,
		long availableGp,
		GeLimitLedger limitLedger,
		GeTradeLedger tradeLedger,
		GeAutoTraderConfig config,
		Instant now)
	{
		List<GeCandidate> result = new ArrayList<>();
		if (snapshot == null || availableGp <= 0 || limitLedger == null
			|| tradeLedger == null || config == null || now == null)
		{
			return result;
		}

		Set<Integer> activeItems = new HashSet<>();
		for (GeTradeObligation obligation : tradeLedger.all())
		{
			activeItems.add(obligation.getItemId());
		}

		for (GeMarketItem item : snapshot.getItems())
		{
			if (item == null || item.isMembers() || activeItems.contains(item.getItemId()))
			{
				continue;
			}
			if (item.getBuyPrice() <= 0 || item.getSellPrice() <= 0 || item.getBuyLimit() <= 0)
			{
				continue;
			}
			if (item.getBuyPrice() > config.maxUnitBuyPrice()
				|| item.getFiveMinuteVolume() < config.minFiveMinuteVolume()
				|| item.unitProfitAfterTax() <= 0
				|| item.roiBasisPoints() < config.minRoiBasisPoints())
			{
				continue;
			}

			int remainingLimit = limitLedger.remaining(item.getItemId(), item.getBuyLimit(), now);
			long capitalQuantity = availableGp / item.getBuyPrice();
			int quantity = (int) Math.min(
				Math.min((long) remainingLimit, capitalQuantity),
				config.maxQuantityPerOffer());
			if (quantity <= 0)
			{
				continue;
			}

			result.add(new GeCandidate(
				item.getItemId(),
				item.getName(),
				item.getBuyPrice(),
				item.getSellPrice(),
				quantity,
				item.unitProfitAfterTax(),
				item.roiBasisPoints(),
				item.getFiveMinuteVolume()));
		}

		result.sort(Comparator.comparingLong(GeCandidate::totalExpectedProfit).reversed()
			.thenComparing(Comparator.comparingInt(GeCandidate::getRoiBasisPoints).reversed())
			.thenComparing(Comparator.comparingInt(GeCandidate::getFiveMinuteVolume).reversed())
			.thenComparingInt(GeCandidate::getItemId));
		return result;
	}
}
