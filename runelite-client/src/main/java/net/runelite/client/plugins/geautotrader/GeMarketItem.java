package net.runelite.client.plugins.geautotrader;

public final class GeMarketItem
{
	private static final int TAX_BASIS_POINTS = 200;
	private static final int TAX_CAP = 5_000_000;

	private final int itemId;
	private final String name;
	private final boolean members;
	private final int buyLimit;
	private final int buyPrice;
	private final int sellPrice;
	private final int fiveMinuteVolume;

	public GeMarketItem(
		int itemId,
		String name,
		boolean members,
		int buyLimit,
		int buyPrice,
		int sellPrice,
		int fiveMinuteVolume)
	{
		this.itemId = itemId;
		this.name = name == null ? "" : name;
		this.members = members;
		this.buyLimit = buyLimit;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
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

	public boolean isMembers()
	{
		return members;
	}

	public int getBuyLimit()
	{
		return buyLimit;
	}

	public int getBuyPrice()
	{
		return buyPrice;
	}

	public int getSellPrice()
	{
		return sellPrice;
	}

	public int getFiveMinuteVolume()
	{
		return fiveMinuteVolume;
	}

	public int sellTax()
	{
		if (sellPrice <= 0)
		{
			return 0;
		}
		long tax = ((long) sellPrice * TAX_BASIS_POINTS) / 10_000L;
		return (int) Math.min(TAX_CAP, tax);
	}

	public int unitProfitAfterTax()
	{
		return sellPrice - sellTax() - buyPrice;
	}

	public int roiBasisPoints()
	{
		if (buyPrice <= 0)
		{
			return 0;
		}
		return (int) (((long) unitProfitAfterTax() * 10_000L) / buyPrice);
	}
}
