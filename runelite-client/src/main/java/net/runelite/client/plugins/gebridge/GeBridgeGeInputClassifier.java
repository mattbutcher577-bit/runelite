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

	static String classify(
		boolean offerSetupOpen,
		int messageLayerMode,
		String prompt,
		boolean fullInputVisible,
		boolean buySetup,
		int setupItemId)
	{
		if (!offerSetupOpen)
		{
			return "NONE";
		}
		if (messageLayerMode == InputType.SEARCH.getType())
		{
			return "ITEM_SEARCH";
		}
		if (messageLayerMode == InputType.NONE.getType())
		{
			return fullInputVisible && buySetup && setupItemId < 0
				? "ITEM_SEARCH"
				: "NONE";
		}
		if (BUY_QUANTITY_PROMPT.equals(prompt) || SELL_QUANTITY_PROMPT.equals(prompt))
		{
			return "QUANTITY";
		}
		if (PRICE_PROMPT.equals(prompt))
		{
			return "PRICE";
		}
		if (fullInputVisible && buySetup && setupItemId < 0)
		{
			return "ITEM_SEARCH";
		}
		return "UNKNOWN";
	}
}
