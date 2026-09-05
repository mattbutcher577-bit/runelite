package net.runelite.client.plugins.gebridge;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

final class GeBridgeGeStateReader
{
	private GeBridgeGeStateReader()
	{
	}

	static GeBridgeGeState read(Client client, GeBridgeInterfaceState interfaceState)
	{
		boolean setupOpen = interfaceState != null && interfaceState.isGrandExchangeOfferSetupOpen();
		boolean detailsVisible = isVisible(client.getWidget(InterfaceID.GeOffers.DETAILS));
		int itemId = setupOpen ? client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) : -1;
		int quantity = setupOpen ? client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) : 0;
		int price = setupOpen ? client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE) : 0;
		String type = setupOpen ? offerType(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)) : "UNKNOWN";

		return new GeBridgeGeState(
			interfaceState != null && interfaceState.isGrandExchangeOpen(),
			setupOpen,
			detailsVisible,
			itemId,
			quantity,
			price,
			type,
			boundsOf(client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER)),
			boundsOf(client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER)),
			boundsOf(client.getWidget(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER)));
	}

	private static String offerType(int value)
	{
		if (value == 0)
		{
			return "BUY";
		}
		if (value == 1)
		{
			return "SELL";
		}
		return "UNKNOWN";
	}

	private static boolean isVisible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private static GeBridgeBounds boundsOf(Widget widget)
	{
		return widget == null || widget.isHidden()
			? GeBridgeBounds.invalid()
			: GeBridgeBounds.from(widget.getBounds());
	}
}
