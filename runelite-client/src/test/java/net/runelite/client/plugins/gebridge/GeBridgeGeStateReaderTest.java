package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeBridgeGeStateReaderTest
{
	@Test
	public void testReadsExactBuySetupValues()
	{
		Client client = mock(Client.class);
		GeBridgeInterfaceState interfaces = new GeBridgeInterfaceState(
			true, true, false, false, false, false, false);

		Widget window = widget(10, 20, 500, 350);
		Widget offer = widget(30, 50, 420, 250);
		Widget inventory = widget(550, 100, 180, 250);

		when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(314);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(1000);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE)).thenReturn(12);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER)).thenReturn(window);
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER)).thenReturn(offer);
		when(client.getWidget(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER)).thenReturn(inventory);

		GeBridgeGeState state = GeBridgeGeStateReader.read(client, interfaces);
		assertEquals(314, state.getOfferSetupItemId());
		assertEquals(1000, state.getOfferSetupQuantity());
		assertEquals(12, state.getOfferSetupPrice());
		assertEquals("BUY", state.getOfferSetupType());
	}

	private static Widget widget(int x, int y, int width, int height)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getBounds()).thenReturn(new Rectangle(x, y, width, height));
		return widget;
	}
}
