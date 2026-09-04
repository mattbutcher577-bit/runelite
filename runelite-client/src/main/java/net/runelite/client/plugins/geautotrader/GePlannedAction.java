package net.runelite.client.plugins.geautotrader;

public final class GePlannedAction
{
	private static final GePlannedAction NONE = new GePlannedAction(
		GePlannedActionType.NONE, 0, -1, "", 0, 0, "");

	private final GePlannedActionType type;
	private final int slot;
	private final int itemId;
	private final String itemName;
	private final int quantity;
	private final int price;
	private final String obligationId;

	private GePlannedAction(
		GePlannedActionType type,
		int slot,
		int itemId,
		String itemName,
		int quantity,
		int price,
		String obligationId)
	{
		this.type = type == null ? GePlannedActionType.NONE : type;
		this.slot = slot;
		this.itemId = itemId;
		this.itemName = itemName == null ? "" : itemName;
		this.quantity = quantity;
		this.price = price;
		this.obligationId = obligationId == null ? "" : obligationId;
	}

	public static GePlannedAction none()
	{
		return NONE;
	}

	public static GePlannedAction of(
		GePlannedActionType type,
		int slot,
		int itemId,
		String itemName,
		int quantity,
		int price,
		String obligationId)
	{
		return new GePlannedAction(type, slot, itemId, itemName, quantity, price, obligationId);
	}

	public GePlannedActionType getType()
	{
		return type;
	}

	public int getSlot()
	{
		return slot;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public int getPrice()
	{
		return price;
	}

	public String getObligationId()
	{
		return obligationId;
	}
}
