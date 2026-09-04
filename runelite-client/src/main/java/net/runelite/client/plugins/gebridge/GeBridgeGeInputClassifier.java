package net.runelite.client.plugins.gebridge;

import net.runelite.api.vars.InputType;

final class GeBridgeGeInputClassifier
{
	private static final String BUY_QUANTITY_PROMPT = "How many do you wish to buy?";
	private static final String SELL_QUANTITY_PROMPT = "How many do you wish to sell?";
	private static final String PRICE_PROMPT = "Set a price for each item:";

	private GeBridgeGeInputClassifier()
	{
	}

	static String classify(boolean offerSetupOpen, int messageLayerMode, String prompt)
	{
		if (!offerSetupOpen)
		{
			return "NONE";
		}
		if (messageLayerMode == InputType.NONE.getType())
		{
			return "NONE";
		}
		if (messageLayerMode == InputType.SEARCH.getType())
		{
			return "ITEM_SEARCH";
		}
		if (BUY_QUANTITY_PROMPT.equals(prompt) || SELL_QUANTITY_PROMPT.equals(prompt))
		{
			return "QUANTITY";
		}
		if (PRICE_PROMPT.equals(prompt))
		{
			return "PRICE";
		}
		return "UNKNOWN";
	}
}
