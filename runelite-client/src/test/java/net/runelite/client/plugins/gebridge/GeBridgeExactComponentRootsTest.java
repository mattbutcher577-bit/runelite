package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeBridgeExactComponentRootsTest
{
	@Test
	public void testDetachedExactRootsExposeSlotCollectAndAbortActions()
	{
		Widget window = visibleRoot(new Rectangle(4, 4, 512, 334));
		Widget open = action("View offer", 27, 85, 115, 110);
		Widget slotRoot = visibleRoot(new Rectangle(27, 85, 115, 110), open);
		Widget collect = action("Collect-items", 430, 245, 36, 36);
		Widget collectRoot = visibleRoot(new Rectangle(420, 235, 80, 50), collect);
		Widget abort = action("Abort offer", 85, 276, 80, 23);
		Widget modifyRoot = visibleRoot(new Rectangle(75, 266, 100, 35), abort);

		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);

		GeBridgeGeActionState state = GeBridgeGeActionReader.read(
			window,
			null,
			new GrandExchangeOffer[]{offer},
			new Widget[]{slotRoot},
			collectRoot,
			modifyRoot,
			101L);

		assertTrue(state.getSlots().get(0).getOpenButton().isValid());
		assertTrue(state.getCollect().isValid());
		assertTrue(state.getAbort().isValid());
	}

	@Test
	public void testDetailsContainerFallbackFindsCollectAndAbortWhenSpecificRootsHaveNoActions()
	{
		Widget window = visibleRoot(new Rectangle(4, 4, 512, 334));
		Widget collectRoot = visibleRoot(new Rectangle(420, 235, 80, 50));
		Widget modifyRoot = visibleRoot(new Rectangle(75, 266, 100, 35));
		Widget collect = action("Collect-note", 430, 245, 36, 36);
		Widget abort = action("Abort offer", 85, 276, 80, 23);
		Widget detailsRoot = visibleRoot(new Rectangle(20, 35, 470, 280), collect, abort);

		GeBridgeGeActionState state = GeBridgeGeActionReader.read(
			window,
			null,
			null,
			null,
			detailsRoot,
			collectRoot,
			modifyRoot,
			102L);

		assertTrue(state.getCollect().isValid());
		assertTrue(state.getAbort().isValid());
	}

	private static Widget visibleRoot(Rectangle bounds, Widget... children)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getBounds()).thenReturn(bounds);
		when(widget.getChildren()).thenReturn(children);
		when(widget.getActions()).thenReturn(null);
		when(widget.getText()).thenReturn("");
		when(widget.getName()).thenReturn("");
		return widget;
	}

	private static Widget action(String action, int x, int y, int width, int height)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getActions()).thenReturn(new String[]{action});
		when(widget.getBounds()).thenReturn(new Rectangle(x, y, width, height));
		when(widget.getText()).thenReturn("");
		when(widget.getName()).thenReturn("");
		return widget;
	}
}
