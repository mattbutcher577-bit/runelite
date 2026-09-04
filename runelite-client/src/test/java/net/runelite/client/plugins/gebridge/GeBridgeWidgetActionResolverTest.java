package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import java.util.List;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeBridgeWidgetActionResolverTest
{
	@Test
	public void testUniqueActionReturnsExactBounds()
	{
		Widget root = mock(Widget.class);
		Widget confirm = widget(new Rectangle(100, 200, 80, 30), new String[]{"Confirm"}, "", "");
		when(root.isHidden()).thenReturn(false);
		when(root.getChildren()).thenReturn(new Widget[]{confirm});

		GeBridgeBounds bounds = GeBridgeWidgetActionResolver.findUnique(root, "Confirm");
		assertTrue(bounds.isValid());
		assertEquals(100, bounds.getX());
		assertEquals(200, bounds.getY());
	}

	@Test
	public void testAmbiguousActionFailsClosed()
	{
		Widget root = mock(Widget.class);
		Widget one = widget(new Rectangle(10, 10, 30, 20), new String[]{"Confirm"}, "", "");
		Widget two = widget(new Rectangle(50, 10, 30, 20), new String[]{"Confirm"}, "", "");
		when(root.isHidden()).thenReturn(false);
		when(root.getChildren()).thenReturn(new Widget[]{one, two});

		assertFalse(GeBridgeWidgetActionResolver.findUnique(root, "Confirm").isValid());
	}

	@Test
	public void testAllActionMatchesAreOrderedTopToBottomThenLeftToRight()
	{
		Widget root = mock(Widget.class);
		Widget bottom = widget(new Rectangle(200, 200, 30, 20), new String[]{"Buy"}, "", "");
		Widget topRight = widget(new Rectangle(200, 100, 30, 20), new String[]{"Buy"}, "", "");
		Widget topLeft = widget(new Rectangle(100, 100, 30, 20), new String[]{"Buy"}, "", "");
		when(root.isHidden()).thenReturn(false);
		when(root.getChildren()).thenReturn(new Widget[]{bottom, topRight, topLeft});

		List<GeBridgeBounds> bounds = GeBridgeWidgetActionResolver.findAll(root, "Buy");
		assertEquals(3, bounds.size());
		assertEquals(100, bounds.get(0).getX());
		assertEquals(200, bounds.get(1).getX());
		assertEquals(200, bounds.get(2).getY());
	}

	@Test
	public void testHiddenOrInvalidBoundsAreIgnored()
	{
		Widget root = mock(Widget.class);
		Widget hidden = widget(new Rectangle(10, 10, 30, 20), new String[]{"Buy"}, "", "");
		when(hidden.isHidden()).thenReturn(true);
		when(root.isHidden()).thenReturn(false);
		when(root.getChildren()).thenReturn(new Widget[]{hidden});
		assertEquals(0, GeBridgeWidgetActionResolver.findAll(root, "Buy").size());
	}

	private static Widget widget(Rectangle bounds, String[] actions, String text, String name)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getBounds()).thenReturn(bounds);
		when(widget.getActions()).thenReturn(actions);
		when(widget.getText()).thenReturn(text);
		when(widget.getName()).thenReturn(name);
		return widget;
	}
}
