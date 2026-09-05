package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
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

		Widget slot1 = slotRoot(InterfaceID.GeOffers.INDEX_0, new Rectangle(20, 50, 100, 70), open1);
		Widget slot2 = slotRoot(InterfaceID.GeOffers.INDEX_1, new Rectangle(140, 50, 95, 70), buy2, sell2);
		Widget slot3 = slotRoot(InterfaceID.GeOffers.INDEX_2, new Rectangle(260, 50, 100, 70), open3);
		Widget window = window(slot1, slot2, slot3);

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

	@Test
	public void testLiveSlotRootsPreventOccupiedOffersFromShiftingEmptyButtons()
	{
		Widget occupiedOpen = action("View offer", 27, 85, 115, 110);
		Widget occupiedBuyAlias = action("Buy", 27, 85, 115, 25);
		Widget occupiedSellAlias = action("Sell", 27, 111, 115, 25);
		Widget textOnlyFalsePositive = text("Create Buy offer", 27, 85, 115, 25);
		Widget buy2 = action("Create Buy offer", 150, 128, 46, 45);
		Widget sell2 = action("Create Sell offer", 206, 128, 46, 45);
		Widget buy3 = action("Create Buy offer", 267, 128, 46, 45);
		Widget sell3 = action("Create Sell offer", 323, 128, 46, 45);

		Widget slot1 = slotRoot(
			InterfaceID.GeOffers.INDEX_0,
			new Rectangle(27, 85, 115, 110),
			occupiedOpen,
			occupiedBuyAlias,
			occupiedSellAlias,
			textOnlyFalsePositive);
		Widget slot2 = slotRoot(
			InterfaceID.GeOffers.INDEX_1,
			new Rectangle(144, 85, 115, 110),
			buy2,
			sell2);
		Widget slot3 = slotRoot(
			InterfaceID.GeOffers.INDEX_2,
			new Rectangle(261, 85, 115, 110),
			buy3,
			sell3);
		Widget window = window(slot1, slot2, slot3);

		GeBridgeGeActionState state = GeBridgeGeActionReader.read(
			window,
			null,
			new GrandExchangeOffer[]{
				offer(GrandExchangeOfferState.BUYING),
				offer(GrandExchangeOfferState.EMPTY),
				offer(GrandExchangeOfferState.EMPTY)},
			99L);

		assertTrue(state.getSlots().get(0).getOpenButton().isValid());
		assertFalse(state.getSlots().get(0).getBuyButton().isValid());
		assertFalse(state.getSlots().get(0).getSellButton().isValid());

		assertEquals(150, state.getSlots().get(1).getBuyButton().getX());
		assertEquals(206, state.getSlots().get(1).getSellButton().getX());
		assertEquals(267, state.getSlots().get(2).getBuyButton().getX());
		assertEquals(323, state.getSlots().get(2).getSellButton().getX());
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state)
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(state);
		return offer;
	}

	private static Widget window(Widget... slots)
	{
		Widget window = mock(Widget.class);
		when(window.isHidden()).thenReturn(false);
		when(window.getBounds()).thenReturn(new Rectangle(4, 4, 512, 334));
		when(window.getActions()).thenReturn(null);
		when(window.getText()).thenReturn("");
		when(window.getName()).thenReturn("");
		when(window.getChildren()).thenReturn(slots);
		return window;
	}

	private static Widget slotRoot(int id, Rectangle bounds, Widget... children)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getId()).thenReturn(id);
		when(widget.getBounds()).thenReturn(bounds);
		when(widget.getActions()).thenReturn(null);
		when(widget.getText()).thenReturn("");
		when(widget.getName()).thenReturn("");
		when(widget.getChildren()).thenReturn(children);
		return widget;
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

	private static Widget text(String text, int x, int y, int width, int height)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getActions()).thenReturn(null);
		when(widget.getText()).thenReturn(text);
		when(widget.getName()).thenReturn("");
		when(widget.getBounds()).thenReturn(new Rectangle(x, y, width, height));
		return widget;
	}
}
