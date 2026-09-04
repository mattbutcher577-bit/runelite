package net.runelite.client.plugins.gebridge;

import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeBridgeLiveGeSectionsTest
{
	@Test
	public void testLiveSectionsExposeSemanticInputAndNeverDefaultToNull()
	{
		GeBridgeLiveGeSections sections = GeBridgeLiveGeSections.read(
			true,
			InputType.SEARCH.getType(),
			null,
			null,
			null,
			null,
			null,
			null,
			77L);

		assertEquals("ITEM_SEARCH", sections.getGeInput().getMode());
		assertEquals(77L, sections.getGeInput().getUpdatedTick());
		assertNotNull(sections.getGeActions());
		assertNotNull(sections.getGeInventory());
		assertFalse(sections.getGeInventory().isOpen());
	}

	@Test
	public void testLiveSectionsUseVisibleInputFallbackForUnknownBuySearch()
	{
		Widget inputField = mock(Widget.class);
		when(inputField.isHidden()).thenReturn(false);

		GeBridgeLiveGeSections sections = GeBridgeLiveGeSections.read(
			true,
			99,
			null,
			inputField,
			true,
			-1,
			null,
			null,
			null,
			null,
			null,
			78L);

		assertEquals("ITEM_SEARCH", sections.getGeInput().getMode());
	}
}
