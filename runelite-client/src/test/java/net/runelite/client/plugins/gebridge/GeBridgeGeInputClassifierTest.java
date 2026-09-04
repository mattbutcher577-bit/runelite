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
			true, InputType.SEARCH.getType(), "anything", false, false, -1));
		assertEquals("NONE", GeBridgeGeInputClassifier.classify(
			false, InputType.SEARCH.getType(), "anything", true, true, -1));
	}

	@Test
	public void testVisibleFullInputMakesUnknownBuySetupItemSearch()
	{
		assertEquals("ITEM_SEARCH", GeBridgeGeInputClassifier.classify(
			true, 99, null, true, true, -1));
	}

	@Test
	public void testVisibleFullInputDoesNotForceSearchAfterItemSelected()
	{
		assertEquals("UNKNOWN", GeBridgeGeInputClassifier.classify(
			true, 99, null, true, true, 1982));
	}

	@Test
	public void testVisibleFullInputDoesNotForceSellSetupToSearch()
	{
		assertEquals("UNKNOWN", GeBridgeGeInputClassifier.classify(
			true, 99, null, true, false, -1));
	}

	@Test
	public void testRecognisedQuantityPromptsBecomeQuantityWithoutPublishingText()
	{
		assertEquals("QUANTITY", GeBridgeGeInputClassifier.classify(
			true, 7, "How many do you wish to buy?", true, true, 1982));
		assertEquals("QUANTITY", GeBridgeGeInputClassifier.classify(
			true, 7, "How many do you wish to sell?", true, false, 1982));
	}

	@Test
	public void testRecognisedPricePromptBecomesPrice()
	{
		assertEquals("PRICE", GeBridgeGeInputClassifier.classify(
			true, 7, "Set a price for each item:", true, true, 1982));
	}

	@Test
	public void testAmbiguousNumericPromptFailsClosed()
	{
		assertEquals("UNKNOWN", GeBridgeGeInputClassifier.classify(
			true, 7, "Enter something else:", false, true, -1));
		assertEquals("UNKNOWN", GeBridgeGeInputClassifier.classify(
			true, 7, null, false, true, -1));
	}

	@Test
	public void testNoMessageLayerMeansNoneWithoutLiveSearchEvidence()
	{
		assertEquals("NONE", GeBridgeGeInputClassifier.classify(
			true, InputType.NONE.getType(), "How many do you wish to buy?", false, true, -1));
	}
}
