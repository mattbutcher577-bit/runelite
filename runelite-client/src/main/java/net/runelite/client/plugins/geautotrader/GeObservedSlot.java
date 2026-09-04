package net.runelite.client.plugins.geautotrader;

public final class GeObservedSlot
{
	private final int slot;
	private final String state;
	private final int itemId;
	private final int totalQuantity;
	private final int filledQuantity;
	private final int price;

	public GeObservedSlot(int slot, String state, int itemId, int totalQuantity, int filledQuantity, int price)
	{
		this.slot = slot;
		this.state = state == null ? "UNKNOWN" : state;
		this.itemId = itemId;
		this.totalQuantity = totalQuantity;
		this.filledQuantity = filledQuantity;
		this.price = price;
	}

	public int getSlot()
	{
		return slot;
	}

	public String getState()
	{
		return state;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getTotalQuantity()
	{
		return totalQuantity;
	}

	public int getFilledQuantity()
	{
		return filledQuantity;
	}

	public int getPrice()
	{
		return price;
	}
}
