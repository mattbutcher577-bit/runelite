package net.runelite.client.plugins.geautotrader;

import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeWidgetActionResolverTest
{
	@Test
	public void testExactWidgetActionIsConvertedToCcOp()
	{
		Widget root = widget(100, -1, -1, null, "root");
		Widget buy = widget(200, 3, -1, new String[]{"Buy"}, "");
		when(root.getChildren()).thenReturn(new Widget[]{buy});

		GeWidgetActionSpec spec = GeWidgetActionResolver.findUnique(root, "Buy");
		assertNotNull(spec);
		assertEquals(3, spec.getParam0());
		assertEquals(200, spec.getParam1());
		assertEquals(1, spec.getIdentifier());
		assertEquals(MenuAction.CC_OP, spec.getAction());
	}

	@Test
	public void testStaticChildIsTraversed()
	{
		Widget root = widget(100, -1, -1, null, "root");
		Widget sell = widget(201, 4, 1127, new String[]{"Sell"}, "");
		when(root.getStaticChildren()).thenReturn(new Widget[]{sell});
		assertNotNull(GeWidgetActionResolver.findUnique(root, "Sell"));
	}

	@Test
	public void testAmbiguousActionFailsClosed()
	{
		Widget root = widget(100, -1, -1, null, "root");
		Widget one = widget(200, 0, -1, new String[]{"Buy"}, "");
		Widget two = widget(201, 1, -1, new String[]{"Buy"}, "");
		when(root.getChildren()).thenReturn(new Widget[]{one, two});
		assertNull(GeWidgetActionResolver.findUnique(root, "Buy"));
	}

	@Test
	public void testHiddenWidgetIsIgnored()
	{
		Widget root = widget(100, -1, -1, null, "root");
		Widget hidden = widget(200, 0, -1, new String[]{"Buy"}, "");
		when(hidden.isHidden()).thenReturn(true);
		when(root.getChildren()).thenReturn(new Widget[]{hidden});
		assertNull(GeWidgetActionResolver.findUnique(root, "Buy"));
	}

	private static Widget widget(int id, int index, int itemId, String[] actions, String name)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(false);
		when(widget.getId()).thenReturn(id);
		when(widget.getIndex()).thenReturn(index);
		when(widget.getItemId()).thenReturn(itemId);
		when(widget.getActions()).thenReturn(actions);
		when(widget.getName()).thenReturn(name);
		return widget;
	}
}
