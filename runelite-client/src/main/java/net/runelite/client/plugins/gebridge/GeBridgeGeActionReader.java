package net.runelite.client.plugins.gebridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

final class GeBridgeGeActionReader
{
	private static final int MAX_WIDGETS = 512;
	private static final String[] COLLECT_ALIASES =
	{
		"Collect",
		"Collect item",
		"Collect items",
		"Collect note",
		"Collect notes",
		"Collect-item",
		"Collect-items",
		"Collect-note",
		"Collect-notes",
		"Collect coins"
	};
	private static final int[] SLOT_COMPONENT_IDS =
	{
		InterfaceID.GeOffers.INDEX_0,
		InterfaceID.GeOffers.INDEX_1,
		InterfaceID.GeOffers.INDEX_2,
		InterfaceID.GeOffers.INDEX_3,
		InterfaceID.GeOffers.INDEX_4,
		InterfaceID.GeOffers.INDEX_5,
		InterfaceID.GeOffers.INDEX_6,
		InterfaceID.GeOffers.INDEX_7
	};

	private GeBridgeGeActionReader()
	{
	}

	static GeBridgeGeActionState read(Widget window, Widget setup, long tick)
	{
		return read(window, setup, null, null, null, null, null, tick);
	}

	static GeBridgeGeActionState read(
		Widget window,
		Widget setup,
		GrandExchangeOffer[] offers,
		long tick)
	{
		return read(window, setup, offers, null, null, null, null, tick);
	}

	static GeBridgeGeActionState read(
		Widget window,
		Widget setup,
		GrandExchangeOffer[] offers,
		Widget[] slotRoots,
		long tick)
	{
		return read(window, setup, offers, slotRoots, null, null, null, tick);
	}

	static GeBridgeGeActionState read(
		Widget window,
		Widget setup,
		GrandExchangeOffer[] offers,
		Widget[] slotRoots,
		Widget collectRoot,
		Widget modifyRoot,
		long tick)
	{
		return read(window, setup, offers, slotRoots, null, collectRoot, modifyRoot, tick);
	}

	static GeBridgeGeActionState read(
		Widget window,
		Widget setup,
		GrandExchangeOffer[] offers,
		Widget[] slotRoots,
		Widget detailsRoot,
		Widget collectRoot,
		Widget modifyRoot,
		long tick)
	{
		if (window == null || window.isHidden())
		{
			return GeBridgeGeActionState.unavailable(tick);
		}

		List<GeBridgeGeActionSlot> slots;
		if (offers != null)
		{
			Widget[] exactRoots = slotRoots == null ? discoverSlotRoots(window) : slotRoots;
			slots = exactSlotLayout(offers, exactRoots);
		}
		else
		{
			List<GeBridgeBounds> buys = GeBridgeWidgetActionResolver.findAllActions(
				window, "Create Buy offer");
			List<GeBridgeBounds> sells = GeBridgeWidgetActionResolver.findAllActions(
				window, "Create Sell offer");
			List<GeBridgeBounds> opens = GeBridgeWidgetActionResolver.findAllActions(
				window, "View offer");
			slots = legacyLayout(buys, sells, opens);
		}

		Widget setupRoot = setup != null && !setup.isHidden() ? setup : window;
		return new GeBridgeGeActionState(
			tick,
			GeBridgeBounds.from(window.getBounds()),
			GeBridgeWidgetActionResolver.findUniqueAction(window, "Back"),
			findFirstOfferStatusAction(collectRoot, detailsRoot, window, COLLECT_ALIASES),
			setup == null || setup.isHidden() ? GeBridgeBounds.invalid() : GeBridgeBounds.from(setup.getBounds()),
			GeBridgeWidgetActionResolver.findUniqueAction(setupRoot, "Choose item", "Select item", "Search"),
			GeBridgeWidgetActionResolver.findUniqueAction(setupRoot, "Quantity", "Set quantity", "Enter quantity"),
			GeBridgeWidgetActionResolver.findUniqueAction(setupRoot, "Price", "Set price", "Enter price"),
			GeBridgeWidgetActionResolver.findUniqueAction(setupRoot, "Confirm"),
			findOfferStatusAction(modifyRoot, detailsRoot, window, "Abort offer", "Abort"),
			slots);
	}

	private static GeBridgeBounds findOfferStatusAction(
		Widget preferred,
		Widget details,
		Widget window,
		String... aliases)
	{
		GeBridgeBounds result = findUniqueVisible(preferred, aliases);
		if (result.isValid())
		{
			return result;
		}
		result = findUniqueVisible(details, aliases);
		if (result.isValid())
		{
			return result;
		}
		return GeBridgeWidgetActionResolver.findUniqueAction(window, aliases);
	}

	private static GeBridgeBounds findFirstOfferStatusAction(
		Widget preferred,
		Widget details,
		Widget window,
		String... aliases)
	{
		GeBridgeBounds result = findFirstVisible(preferred, aliases);
		if (result.isValid())
		{
			return result;
		}
		result = findFirstVisible(details, aliases);
		if (result.isValid())
		{
			return result;
		}
		return first(GeBridgeWidgetActionResolver.findAllActions(window, aliases));
	}

	private static GeBridgeBounds findUniqueVisible(Widget root, String... aliases)
	{
		return root == null || root.isHidden()
			? GeBridgeBounds.invalid()
			: GeBridgeWidgetActionResolver.findUniqueAction(root, aliases);
	}

	private static GeBridgeBounds findFirstVisible(Widget root, String... aliases)
	{
		return root == null || root.isHidden()
			? GeBridgeBounds.invalid()
			: first(GeBridgeWidgetActionResolver.findAllActions(root, aliases));
	}

	private static GeBridgeBounds first(List<GeBridgeBounds> bounds)
	{
		return bounds == null || bounds.isEmpty() ? GeBridgeBounds.invalid() : bounds.get(0);
	}

	private static Widget[] discoverSlotRoots(Widget window)
	{
		Widget[] result = new Widget[SLOT_COMPONENT_IDS.length];
		Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<Widget> queue = new ArrayDeque<>();
		queue.add(window);

		while (!queue.isEmpty() && seen.size() < MAX_WIDGETS)
		{
			Widget widget = queue.removeFirst();
			if (widget == null || !seen.add(widget) || widget.isHidden())
			{
				continue;
			}
			int id = widget.getId();
			for (int slot = 0; slot < SLOT_COMPONENT_IDS.length; slot++)
			{
				if (id == SLOT_COMPONENT_IDS[slot])
				{
					result[slot] = widget;
					break;
				}
			}
			enqueue(queue, widget.getChildren());
			enqueue(queue, widget.getDynamicChildren());
			enqueue(queue, widget.getStaticChildren());
			enqueue(queue, widget.getNestedChildren());
		}
		return result;
	}

	private static void enqueue(Deque<Widget> queue, Widget[] children)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			if (child != null)
			{
				queue.addLast(child);
			}
		}
	}

	private static List<GeBridgeGeActionSlot> exactSlotLayout(
		GrandExchangeOffer[] offers,
		Widget[] slotRoots)
	{
		List<GeBridgeGeActionSlot> slots = new ArrayList<>();
		for (int slot = 0; slot < offers.length; slot++)
		{
			GrandExchangeOffer offer = offers[slot];
			GrandExchangeOfferState state = offer == null || offer.getState() == null
				? GrandExchangeOfferState.EMPTY
				: offer.getState();
			Widget root = slotRoots != null && slot < slotRoots.length ? slotRoots[slot] : null;

			GeBridgeBounds buy = GeBridgeBounds.invalid();
			GeBridgeBounds sell = GeBridgeBounds.invalid();
			GeBridgeBounds open = GeBridgeBounds.invalid();
			GeBridgeBounds rootBounds = visibleBounds(root);

			if (root != null && !root.isHidden())
			{
				if (state == GrandExchangeOfferState.EMPTY)
				{
					buy = GeBridgeWidgetActionResolver.findUniqueAction(root, "Create Buy offer");
					sell = GeBridgeWidgetActionResolver.findUniqueAction(root, "Create Sell offer");
				}
				else
				{
					open = GeBridgeWidgetActionResolver.findUniqueAction(root, "View offer");
				}
			}

			GeBridgeBounds slotBounds = rootBounds.isValid()
				? rootBounds
				: GeBridgeBounds.union(buy, sell, open);
			slots.add(new GeBridgeGeActionSlot(slot, slotBounds, buy, sell, open));
		}
		return slots;
	}

	private static GeBridgeBounds visibleBounds(Widget widget)
	{
		return widget == null || widget.isHidden()
			? GeBridgeBounds.invalid()
			: GeBridgeBounds.from(widget.getBounds());
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
