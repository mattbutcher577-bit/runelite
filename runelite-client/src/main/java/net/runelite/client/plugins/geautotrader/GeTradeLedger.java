package net.runelite.client.plugins.geautotrader;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GeTradeLedger
{
	private final Map<String, GeTradeObligation> obligations = new LinkedHashMap<>();

	public GeTradeObligation reserveBuy(String id, int slot, int itemId, String itemName, int quantity, int price)
	{
		validateNew(id, slot, quantity, price);
		GeTradeObligation obligation = new GeTradeObligation(
			id, slot, GeTradeSide.BUY, itemId, itemName, quantity, price, null);
		obligations.put(id, obligation);
		return obligation;
	}

	public GeTradeObligation createSell(
		String id,
		String parentId,
		int slot,
		int itemId,
		String itemName,
		int quantity,
		int price)
	{
		validateNew(id, slot, quantity, price);
		GeTradeObligation obligation = new GeTradeObligation(
			id, slot, GeTradeSide.SELL, itemId, itemName, quantity, price, parentId);
		obligations.put(id, obligation);
		return obligation;
	}

	public void markPlaced(String id, Instant placedAt)
	{
		require(id).markPlaced(placedAt);
	}

	public void markFilled(String id, int quantity)
	{
		require(id).markFilled(quantity);
	}

	public void incrementAbortCount(String id)
	{
		require(id).incrementAbortCount();
	}

	public long reservedGp()
	{
		long total = 0L;
		for (GeTradeObligation obligation : obligations.values())
		{
			total = Math.addExact(total, obligation.outstandingReservedGp());
		}
		return total;
	}

	public Collection<GeTradeObligation> all()
	{
		return Collections.unmodifiableCollection(obligations.values());
	}

	public GeTradeObligation get(String id)
	{
		return obligations.get(id);
	}

	private GeTradeObligation require(String id)
	{
		GeTradeObligation obligation = obligations.get(id);
		if (obligation == null)
		{
			throw new IllegalArgumentException("unknown obligation: " + id);
		}
		return obligation;
	}

	private static void validateNew(String id, int slot, int quantity, int price)
	{
		if (id == null || id.trim().isEmpty())
		{
			throw new IllegalArgumentException("id required");
		}
		if (!GeSafetyPolicy.canUseSlot(slot))
		{
			throw new IllegalArgumentException("automation may only use GE slots 1-3");
		}
		if (quantity <= 0 || price <= 0)
		{
			throw new IllegalArgumentException("quantity and price must be positive");
		}
	}
}
