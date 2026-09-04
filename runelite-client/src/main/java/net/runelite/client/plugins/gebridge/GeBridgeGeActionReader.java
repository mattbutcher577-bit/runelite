package net.runelite.client.plugins.gebridge;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;

final class GeBridgeGeActionReader
{
	private GeBridgeGeActionReader()
	{
	}

	static GeBridgeGeActionState read(Widget window, Widget setup, long tick)
	{
		return read(window, setup, null, tick);
	}

	static GeBridgeGeActionState read(
		Widget window,
		Widget setup,
		GrandExchangeOffer[] offers,
		long tick)
	{
		if (window == null || window.isHidden())
		{
			return GeBridgeGeActionState.unavailable(tick);
		}

		List<GeBridgeBounds> buys = GeBridgeWidgetActionResolver.findAll(
			window, "Create Buy offer", "Buy");
		List<GeBridgeBounds> sells = GeBridgeWidgetActionResolver.findAll(
			window, "Create Sell offer", "Sell");
		List<GeBridgeBounds> opens = GeBridgeWidgetActionResolver.findAll(window, "View offer", "View");
		List<GeBridgeGeActionSlot> slots = offers == null
			? legacyLayout(buys, sells, opens)
			: offerAlignedLayout(offers, buys, sells, opens);

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

	private static List<GeBridgeGeActionSlot> offerAlignedLayout(
		GrandExchangeOffer[] offers,
		List<GeBridgeBounds> buys,
		List<GeBridgeBounds> sells,
		List<GeBridgeBounds> opens)
	{
		List<GeBridgeGeActionSlot> slots = new ArrayList<>();
		int buyIndex = 0;
		int sellIndex = 0;
		int openIndex = 0;

		for (int slot = 0; slot < offers.length; slot++)
		{
			GrandExchangeOffer offer = offers[slot];
			GrandExchangeOfferState state = offer == null || offer.getState() == null
				? GrandExchangeOfferState.EMPTY
				: offer.getState();

			GeBridgeBounds buy = GeBridgeBounds.invalid();
			GeBridgeBounds sell = GeBridgeBounds.invalid();
			GeBridgeBounds open = GeBridgeBounds.invalid();

			if (state == GrandExchangeOfferState.EMPTY)
			{
				buy = at(buys, buyIndex++);
				sell = at(sells, sellIndex++);
			}
			else
			{
				open = at(opens, openIndex++);
			}

			slots.add(new GeBridgeGeActionSlot(
				slot,
				GeBridgeBounds.union(buy, sell, open),
				buy,
				sell,
				open));
		}
		return slots;
	}

	private static List<GeBridgeGeActionSlot> legacyLayout(
		List<GeBridgeBounds> buys,
		List<GeBridgeBounds> sells,
		List<GeBridgeBounds> opens)
	{
		int slotCount = Math.max(buys.size(), Math.max(sells.size(), opens.size()));
		List<GeBridgeGeActionSlot> slots = new ArrayList<>();
		for (int slot = 0; slot < slotCount; slot++)
		{
			GeBridgeBounds buy = at(buys, slot);
			GeBridgeBounds sell = at(sells, slot);
			GeBridgeBounds open = at(opens, slot);
			slots.add(new GeBridgeGeActionSlot(slot, GeBridgeBounds.union(buy, sell, open), buy, sell, open));
		}
		return slots;
	}

	private static GeBridgeBounds at(List<GeBridgeBounds> values, int index)
	{
		return index >= 0 && index < values.size() ? values.get(index) : GeBridgeBounds.invalid();
	}
}
