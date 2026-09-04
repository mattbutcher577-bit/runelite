package net.runelite.client.plugins.gebridge;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.widgets.Widget;

final class GeBridgeGeActionReader
{
	private GeBridgeGeActionReader()
	{
	}

	static GeBridgeGeActionState read(Widget window, Widget setup, long tick)
	{
		if (window == null || window.isHidden())
		{
			return GeBridgeGeActionState.unavailable(tick);
		}

		List<GeBridgeBounds> buys = GeBridgeWidgetActionResolver.findAll(window, "Buy");
		List<GeBridgeBounds> sells = GeBridgeWidgetActionResolver.findAll(window, "Sell");
		List<GeBridgeBounds> opens = GeBridgeWidgetActionResolver.findAll(window, "View offer", "View");
		int slotCount = Math.max(buys.size(), Math.max(sells.size(), opens.size()));
		List<GeBridgeGeActionSlot> slots = new ArrayList<>();
		for (int slot = 0; slot < slotCount; slot++)
		{
			GeBridgeBounds buy = at(buys, slot);
			GeBridgeBounds sell = at(sells, slot);
			GeBridgeBounds open = at(opens, slot);
			slots.add(new GeBridgeGeActionSlot(slot, GeBridgeBounds.union(buy, sell, open), buy, sell, open));
		}

		Widget setupRoot = setup != null && !setup.isHidden() ? setup : window;
		return new GeBridgeGeActionState(
			tick,
			GeBridgeBounds.from(window.getBounds()),
			GeBridgeWidgetActionResolver.findUnique(window, "Back"),
			GeBridgeWidgetActionResolver.findUnique(window, "Collect", "Collect items", "Collect coins"),
			setup == null || setup.isHidden() ? GeBridgeBounds.invalid() : GeBridgeBounds.from(setup.getBounds()),
			GeBridgeWidgetActionResolver.findUnique(setupRoot, "Choose item", "Select item", "Search"),
			GeBridgeWidgetActionResolver.findUnique(setupRoot, "Quantity", "Set quantity", "Enter quantity"),
			GeBridgeWidgetActionResolver.findUnique(setupRoot, "Price", "Set price", "Enter price"),
			GeBridgeWidgetActionResolver.findUnique(setupRoot, "Confirm"),
			GeBridgeWidgetActionResolver.findUnique(window, "Abort offer", "Abort"),
			slots);
	}

	private static GeBridgeBounds at(List<GeBridgeBounds> values, int index)
	{
		return index >= 0 && index < values.size() ? values.get(index) : GeBridgeBounds.invalid();
	}
}
