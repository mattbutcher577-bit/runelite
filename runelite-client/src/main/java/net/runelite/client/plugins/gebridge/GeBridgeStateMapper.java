package net.runelite.client.plugins.gebridge;

import net.runelite.api.GrandExchangeOfferState;

final class GeBridgeStateMapper
{
	private GeBridgeStateMapper()
	{
	}

	static String visualFor(GrandExchangeOfferState state)
	{
		if (state == null)
		{
			return "EMPTY";
		}

		switch (state)
		{
			case BUYING:
			case SELLING:
				return "ORANGE";
			case BOUGHT:
			case SOLD:
				return "GREEN";
			case CANCELLED_BUY:
			case CANCELLED_SELL:
				return "RED";
			case EMPTY:
			default:
				return "EMPTY";
		}
	}

	static boolean collectReady(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL;
	}
}
