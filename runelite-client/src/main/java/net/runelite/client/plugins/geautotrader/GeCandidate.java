package net.runelite.client.plugins.geautotrader;

public final class GeCandidate
{
	private final int itemId;
	private final String name;
	private final int buyPrice;
	private final int sellPrice;
	private final int quantity;
	private final int unitProfit;
	private final int roiBasisPoints;
	private final int fiveMinuteVolume;

	public GeCandidate(
		int itemId,
		String name,
		int buyPrice,
		int sellPrice,
		int quantity,
		int unitProfit,
		int roiBasisPoints,
		int fiveMinuteVolume)
	{
		this.itemId = itemId;
		this.name = name == null ? "" : name;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.quantity = quantity;
		this.unitProfit = unitProfit;
		this.roiBasisPoints = roiBasisPoints;
		this.fiveMinuteVolume = fiveMinuteVolume;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getName()
	{
		return name;
	}

	public int getBuyPrice()
	{
		return buyPrice;
	}

	public int getSellPrice()
	{
		return sellPrice;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public int getUnitProfit()
	{
		return unitProfit;
	}

	public int getRoiBasisPoints()
	{
		return roiBasisPoints;
	}

	public int getFiveMinuteVolume()
	{
		return fiveMinuteVolume;
	}

	public long totalExpectedProfit()
	{
		return (long) quantity * unitProfit;
	}
}
