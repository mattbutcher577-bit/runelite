package net.runelite.client.plugins.gebridge;

import net.runelite.api.vars.InputType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

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
}
