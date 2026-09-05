package net.runelite.client.plugins.geautotrader;

import java.util.List;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;

public final class GeSlotActionResolver
{
	private GeSlotActionResolver()
	{
	}

	public static GeWidgetActionSpec resolveBuy(Widget window, GrandExchangeOffer[] offers, int oneBasedSlot)
	{
		return resolveEmptyAction(window, offers, oneBasedSlot, "Buy");
	}

	public static GeWidgetActionSpec resolveSell(Widget window, GrandExchangeOffer[] offers, int oneBasedSlot)
	{
		return resolveEmptyAction(window, offers, oneBasedSlot, "Sell");
	}

	public static GeWidgetActionSpec resolveOpen(Widget window, GrandExchangeOffer[] offers, int oneBasedSlot)
	{
		if (!validSlot(offers, oneBasedSlot))
		{
			return null;
		}
		int target = oneBasedSlot - 1;
		if (state(offers[target]) == GrandExchangeOfferState.EMPTY)
		{
			return null;
		}
		List<GeWidgetActionSpec> actions = GeWidgetActionResolver.findAll(window, "View offer", "View");
		int expected = 0;
		int index = -1;
		for (int i = 0; i < offers.length; i++)
		{
			if (state(offers[i]) != GrandExchangeOfferState.EMPTY)
			{
				if (i == target)
				{
					index = expected;
				}
				expected++;
			}
		}
		return expected == actions.size() && index >= 0 ? actions.get(index) : null;
	}

	private static GeWidgetActionSpec resolveEmptyAction(
		Widget window,
		GrandExchangeOffer[] offers,
		int oneBasedSlot,
		String actionName)
	{
		if (!validSlot(offers, oneBasedSlot))
		{
			return null;
		}
		int target = oneBasedSlot - 1;
		if (state(offers[target]) != GrandExchangeOfferState.EMPTY)
		{
			return null;
		}
		List<GeWidgetActionSpec> actions = GeWidgetActionResolver.findAll(window, actionName);
		int expected = 0;
		int index = -1;
		for (int i = 0; i < offers.length; i++)
		{
			if (state(offers[i]) == GrandExchangeOfferState.EMPTY)
			{
				if (i == target)
				{
					index = expected;
				}
				expected++;
			}
		}
		return expected == actions.size() && index >= 0 ? actions.get(index) : null;
	}

	private static boolean validSlot(GrandExchangeOffer[] offers, int oneBasedSlot)
	{
		return GeSafetyPolicy.canUseSlot(oneBasedSlot)
			&& offers != null
			&& oneBasedSlot <= offers.length;
	}

	private static GrandExchangeOfferState state(GrandExchangeOffer offer)
	{
		return offer == null || offer.getState() == null
			? GrandExchangeOfferState.EMPTY
			: offer.getState();
	}
}
