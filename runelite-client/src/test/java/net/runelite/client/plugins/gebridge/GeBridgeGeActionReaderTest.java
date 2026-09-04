package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeBridgeGeActionReaderTest
{
	@Test
	public void testMixedOccupiedAndEmptySlotsKeepRealSlotIdentity()
	{
		Widget open1 = action("View offer", 20, 50, 100, 70);
		Widget buy2 = action("Create Buy offer", 140, 50, 45, 30);
		Widget sell2 = action("Create Sell offer", 190, 50, 45, 30);
		Widget open3 = action("View offer", 260, 50, 100, 70);

		Widget window = mock(Widget.class);
		when(window.isHidden()).thenReturn(false);
		when(window.getBounds()).thenReturn(new Rectangle(0, 0, 500, 300));
		when(window.getActions()).thenReturn(null);
		when(window.getText()).thenReturn("");
		when(window.getName()).thenReturn("");
		when(window.getChildren()).thenReturn(new Widget[]{open1, buy2, sell2, open3});

		GrandExchangeOffer occupied1 = offer(GrandExchangeOfferState.BUYING);
		GrandExchangeOffer empty2 = offer(GrandExchangeOfferState.EMPTY);
		GrandExchangeOffer occupied3 = offer(GrandExchangeOfferState.BOUGHT);

		GeBridgeGeActionState state = GeBridgeGeActionReader.read(
			window,
			null,
			new GrandExchangeOffer[]{occupied1, empty2, occupied3},
			77L);

		assertEquals(3, state.getSlots().size());
		assertEquals(0, state.getSlots().get(0).getSlot());
		assertTrue(state.getSlots().get(0).getOpenButton().isValid());
		assertFalse(state.getSlots().get(0).getBuyButton().isValid());

		assertEquals(1, state.getSlots().get(1).getSlot());
		assertTrue(state.getSlots().get(1).getBuyButton().isValid());
		assertTrue(state.getSlots().get(1).getSellButton().isValid());
		assertFalse(state.getSlots().get(1).getOpenButton().isValid());

		assertEquals(2, state.getSlots().get(2).getSlot());
		assertTrue(state.getSlots().get(2).getOpenButton().isValid());
		assertFalse(state.getSlots().get(2).getBuyButton().isValid());
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state)
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(state);
		return offer;
	}

	private static Widget action(String action, int x, int y, int width, int height)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getActions()).thenReturn(new String[]{action});
		when(widget.getText()).thenReturn("");
		when(widget.getName()).thenReturn("");
		when(widget.getBounds()).thenReturn(new Rectangle(x, y, width, height));
		when(widget.getChildren()).thenReturn(null);
		return widget;
	}
}
