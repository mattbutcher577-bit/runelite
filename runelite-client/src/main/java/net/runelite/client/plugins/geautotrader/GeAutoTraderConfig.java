package net.runelite.client.plugins.geautotrader;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("geautotraderv6")
public interface GeAutoTraderConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enable auto-trader",
		description = "Enable V6 automatic F2P Grand Exchange trading",
		position = 0
	)
	default boolean enabled()
	{
		return false;
	}

	@Range(min = 0, max = 10000)
	@ConfigItem(
		keyName = "minRoiBasisPoints",
		name = "Minimum ROI (bp)",
		description = "Minimum after-tax ROI in basis points; 100 = 1%",
		position = 1
	)
	default int minRoiBasisPoints()
	{
		return 100;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "minFiveMinuteVolume",
		name = "Minimum 5m volume",
		description = "Minimum recent five-minute traded volume",
		position = 2
	)
	default int minFiveMinuteVolume()
	{
		return 10;
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "maxUnitBuyPrice",
		name = "Maximum unit buy price",
		description = "Hard maximum GP paid for one item",
		position = 3
	)
	default int maxUnitBuyPrice()
	{
		return 20_000_000;
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "maxQuantityPerOffer",
		name = "Maximum quantity per offer",
		description = "Additional per-offer quantity ceiling",
		position = 4
	)
	default int maxQuantityPerOffer()
	{
		return 1000;
	}

	@Range(min = 5, max = 300)
	@ConfigItem(
		keyName = "marketRefreshSeconds",
		name = "Market refresh seconds",
		description = "How often public price data may be refreshed",
		position = 5
	)
	default int marketRefreshSeconds()
	{
		return 30;
	}
}
