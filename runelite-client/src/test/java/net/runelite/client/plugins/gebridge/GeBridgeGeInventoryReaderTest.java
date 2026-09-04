package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeBridgeGeInventoryReaderTest
{
	@Test
	public void testVisibleWidgetItemsKeepRawAndCanonicalIds()
	{
		Widget container = mock(Widget.class);
		Widget item = mock(Widget.class);
		ItemManager itemManager = mock(ItemManager.class);

		when(container.isHidden()).thenReturn(false);
		when(container.getChildren()).thenReturn(new Widget[]{item});
		when(item.isHidden()).thenReturn(false);
		when(item.getItemId()).thenReturn(799);
		when(item.getItemQuantity()).thenReturn(12);
		when(item.getBounds()).thenReturn(new Rectangle(550, 220, 32, 32));
		when(itemManager.canonicalize(799)).thenReturn(798);

		GeBridgeGeInventoryState state = GeBridgeGeInventoryReader.read(container, itemManager, 77L);
		assertTrue(state.isOpen());
		assertEquals(77L, state.getUpdatedTick());
		assertEquals(1, state.getEntries().size());
		GeBridgeGeInventoryEntry entry = state.getEntries().get(0);
		assertEquals(0, entry.getInventorySlot());
		assertEquals(799, entry.getRawItemId());
		assertEquals(798, entry.getCanonicalItemId());
		assertEquals(12, entry.getQuantity());
		assertTrue(entry.getBounds().isValid());
	}

	@Test
	public void testHiddenOrMissingContainerIsClosedAndInvalidItemsAreSkipped()
	{
		ItemManager itemManager = mock(ItemManager.class);
		assertEquals(0, GeBridgeGeInventoryReader.read(null, itemManager, 1L).getEntries().size());

		Widget hidden = mock(Widget.class);
		when(hidden.isHidden()).thenReturn(true);
		assertEquals(0, GeBridgeGeInventoryReader.read(hidden, itemManager, 1L).getEntries().size());

		Widget container = mock(Widget.class);
		Widget empty = mock(Widget.class);
		when(container.isHidden()).thenReturn(false);
		when(container.getChildren()).thenReturn(new Widget[]{empty});
		when(empty.isHidden()).thenReturn(false);
		when(empty.getItemId()).thenReturn(-1);
		when(empty.getItemQuantity()).thenReturn(0);
		assertEquals(0, GeBridgeGeInventoryReader.read(container, itemManager, 2L).getEntries().size());
	}
}
