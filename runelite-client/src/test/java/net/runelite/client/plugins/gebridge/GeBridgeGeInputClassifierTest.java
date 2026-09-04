package net.runelite.client.plugins.gebridge;

import net.runelite.api.vars.InputType;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeBridgeGeInputClassifierTest
{
	@Test
	public void testSearchModeIsItemSearchOnlyInsideOfferSetup()
	{
		assertEquals("ITEM_SEARCH", GeBridgeGeInputClassifier.classify(
			true, InputType.SEARCH.getType(), "anything"));
		assertEquals("NONE", GeBridgeGeInputClassifier.classify(
			false, InputType.SEARCH.getType(), "anything"));
	}

	@Test
	public void testRecognisedQuantityPromptsBecomeQuantityWithoutPublishingText()
	{
		assertEquals("QUANTITY", GeBridgeGeInputClassifier.classify(
			true, 7, "How many do you wish to buy?"));
		assertEquals("QUANTITY", GeBridgeGeInputClassifier.classify(
			true, 7, "How many do you wish to sell?"));
	}

	@Test
	public void testRecognisedPricePromptBecomesPrice()
	{
		assertEquals("PRICE", GeBridgeGeInputClassifier.classify(
			true, 7, "Set a price for each item:"));
	}

	@Test
	public void testAmbiguousNumericPromptFailsClosed()
	{
		assertEquals("UNKNOWN", GeBridgeGeInputClassifier.classify(
			true, 7, "Enter something else:"));
		assertEquals("UNKNOWN", GeBridgeGeInputClassifier.classify(
			true, 7, null));
	}

	@Test
	public void testNoMessageLayerMeansNone()
	{
		assertEquals("NONE", GeBridgeGeInputClassifier.classify(
			true, InputType.NONE.getType(), "How many do you wish to buy?"));
	}
}
