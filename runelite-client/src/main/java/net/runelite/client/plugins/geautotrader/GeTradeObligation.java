package net.runelite.client.plugins.geautotrader;

import java.time.Instant;

public final class GeTradeObligation
{
	private final String id;
	private final int slot;
	private final GeTradeSide side;
	private final int itemId;
	private final String itemName;
	private final int intendedQuantity;
	private final int intendedPrice;
	private final String parentId;
	private final int targetSellPrice;
	private Instant placedAt;
	private int filledQuantity;
	private int abortCount;

	GeTradeObligation(
		String id,
		int slot,
		GeTradeSide side,
		int itemId,
		String itemName,
		int intendedQuantity,
		int intendedPrice,
		String parentId)
	{
		this(id, slot, side, itemId, itemName, intendedQuantity, intendedPrice, parentId,
			side == GeTradeSide.SELL ? intendedPrice : 0);
	}

	GeTradeObligation(
		String id,
		int slot,
		GeTradeSide side,
		int itemId,
		String itemName,
		int intendedQuantity,
		int intendedPrice,
		String parentId,
		int targetSellPrice)
	{
		this.id = id;
		this.slot = slot;
		this.side = side;
		this.itemId = itemId;
		this.itemName = itemName == null ? "" : itemName;
		this.intendedQuantity = intendedQuantity;
		this.intendedPrice = intendedPrice;
		this.parentId = parentId;
		this.targetSellPrice = Math.max(0, targetSellPrice);
	}

	public String getId()
	{
		return id;
	}

	public int getSlot()
	{
		return slot;
	}

	public GeTradeSide getSide()
	{
		return side;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName;
	}

	public int getIntendedQuantity()
	{
		return intendedQuantity;
	}

	public int getIntendedPrice()
	{
		return intendedPrice;
	}

	public String getParentId()
	{
		return parentId;
	}

	public int getTargetSellPrice()
	{
		return targetSellPrice;
	}

	public Instant getPlacedAt()
	{
		return placedAt;
	}

	public int getFilledQuantity()
	{
		return filledQuantity;
	}

	public int getAbortCount()
	{
		return abortCount;
	}

	void markPlaced(Instant placedAt)
	{
		this.placedAt = placedAt;
	}

	void markFilled(int quantity)
	{
		this.filledQuantity = Math.max(0, Math.min(quantity, intendedQuantity));
	}

	void incrementAbortCount()
	{
		this.abortCount++;
	}

	long outstandingReservedGp()
	{
		if (side != GeTradeSide.BUY)
		{
			return 0L;
		}
		int outstanding = Math.max(0, intendedQuantity - filledQuantity);
		return (long) outstanding * (long) intendedPrice;
	}
}
