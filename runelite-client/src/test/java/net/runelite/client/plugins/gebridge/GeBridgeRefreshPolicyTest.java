package net.runelite.client.plugins.gebridge;

import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetInfo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeBridgeRefreshPolicyTest
{
	@Test
	public void testGeSearchAndOfferSetupScriptsRefreshState()
	{
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshScript(ScriptID.GE_ITEM_SEARCH));
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshScript(ScriptID.GE_OFFERS_SETUP_BUILD));
		assertFalse(GeBridgeRefreshPolicy.shouldRefreshScript(-1));
	}

	@Test
	public void testMessageLayerModeRefreshesSemanticInputState()
	{
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshVarClient(VarClientID.MESLAYERMODE));
		assertFalse(GeBridgeRefreshPolicy.shouldRefreshVarClient(-1));
	}

	@Test
	public void testGeOfferSetupVarChangesRefreshExactSetupState()
	{
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshVarbit(VarbitID.GE_NEWOFFER_QUANTITY));
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshVarbit(VarbitID.GE_NEWOFFER_PRICE));
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshVarbit(VarbitID.GE_NEWOFFER_TYPE));
		assertFalse(GeBridgeRefreshPolicy.shouldRefreshVarbit(-1));
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshVarp(VarPlayerID.TRADINGPOST_SEARCH));
		assertFalse(GeBridgeRefreshPolicy.shouldRefreshVarp(-1));
	}

	@Test
	public void testOnlyGrandExchangeWidgetGroupRefreshesGeState()
	{
		int geGroup = WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER.getGroupId();
		assertTrue(GeBridgeRefreshPolicy.shouldRefreshWidgetGroup(geGroup));
		assertFalse(GeBridgeRefreshPolicy.shouldRefreshWidgetGroup(geGroup + 1));
	}
}
