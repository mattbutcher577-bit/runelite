package net.runelite.client.plugins.gebridge;

import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetInfo;

final class GeBridgeRefreshPolicy
{
	private GeBridgeRefreshPolicy()
	{
	}

	static boolean shouldRefreshScript(int scriptId)
	{
		return scriptId == ScriptID.GE_ITEM_SEARCH
			|| scriptId == ScriptID.GE_OFFERS_SETUP_BUILD;
	}

	static boolean shouldRefreshVarClient(int index)
	{
		return index == VarClientID.MESLAYERMODE;
	}

	static boolean shouldRefreshVarbit(int varbitId)
	{
		return varbitId == VarbitID.GE_NEWOFFER_QUANTITY
			|| varbitId == VarbitID.GE_NEWOFFER_PRICE
			|| varbitId == VarbitID.GE_NEWOFFER_TYPE;
	}

	static boolean shouldRefreshVarp(int varpId)
	{
		return varpId == VarPlayerID.TRADINGPOST_SEARCH;
	}

	static boolean shouldRefreshWidgetGroup(int groupId)
	{
		return groupId == WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER.getGroupId();
	}
}
