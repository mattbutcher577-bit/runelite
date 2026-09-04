package net.runelite.client.plugins.geautotrader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

public final class GeActionDispatcher
{
	private final Client client;
	private final GeExecutionService execution;
	private final GePromptInputService promptInput;

	public GeActionDispatcher(Client client, GeExecutionService execution, GePromptInputService promptInput)
	{
		this.client = client;
		this.execution = execution;
		this.promptInput = promptInput;
	}

	public GeReasonCode dispatch(GePlannedAction action, GeObservedState state)
	{
		if (action == null || action.getType() == GePlannedActionType.NONE)
		{
			return GeReasonCode.OK;
		}

		switch (action.getType())
		{
			case OPEN_BUY:
				return execute(slotAction(state, action.getSlot(), true, "Buy"));
			case OPEN_SELL:
				return execute(slotAction(state, action.getSlot(), true, "Sell"));
			case OPEN_OFFER:
				return execute(slotAction(state, action.getSlot(), false, "View offer", "View"));
			case TYPE_ITEM_SEARCH:
				return promptInput.typeItemSearch(action.getItemName(), state);
			case SELECT_ITEM:
				return execute(findItemAction(action.getItemId(), false));
			case SELECT_SELL_ITEM:
				return execute(findItemAction(action.getItemId(), true));
			case OPEN_QUANTITY:
				return execute(GeWidgetActionResolver.findUnique(setupRoot(),
					"Quantity", "Set quantity", "Enter quantity"));
			case TYPE_QUANTITY:
				return promptInput.typeQuantity(action.getQuantity(), state);
			case OPEN_PRICE:
				return execute(GeWidgetActionResolver.findUnique(setupRoot(),
					"Price", "Set price", "Enter price"));
			case TYPE_PRICE:
				return promptInput.typePrice(action.getPrice(), state);
			case CONFIRM:
				return execute(GeWidgetActionResolver.findUnique(setupRoot(), "Confirm"));
			case ABORT_BUY:
				return execute(GeWidgetActionResolver.findUnique(setupRoot(), "Abort offer", "Abort"));
			case COLLECT:
				return execute(GeWidgetActionResolver.findUnique(setupRoot(),
					"Collect", "Collect items", "Collect coins"));
			default:
				return GeReasonCode.EXECUTION_REJECTED;
		}
	}

	private GeWidgetActionSpec slotAction(
		GeObservedState state,
		int targetSlot,
		boolean empty,
		String... aliases)
	{
		Widget window = visible(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
		if (window == null || state == null || !GeSafetyPolicy.canUseSlot(targetSlot))
		{
			return null;
		}
		List<GeWidgetActionSpec> actions = GeWidgetActionResolver.findAll(window, aliases);
		List<GeObservedSlot> slots = new ArrayList<>(state.getSlots());
		slots.sort(Comparator.comparingInt(GeObservedSlot::getSlot));
		int actionIndex = 0;
		for (GeObservedSlot slot : slots)
		{
			if (slot == null)
			{
				continue;
			}
			boolean matches = empty ? slot.isEmpty() : !slot.isEmpty();
			if (!matches)
			{
				continue;
			}
			if (slot.getSlot() == targetSlot)
			{
				return actionIndex < actions.size() ? actions.get(actionIndex) : null;
			}
			actionIndex++;
		}
		return null;
	}

	private GeWidgetActionSpec findItemAction(int itemId, boolean sellInventory)
	{
		Widget root = sellInventory
			? visible(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER)
			: setupRoot();
		if (root == null)
		{
			return null;
		}
		GeWidgetActionSpec spec = sellInventory
			? GeWidgetActionResolver.findUniqueItem(root, itemId, "Offer", "Sell")
			: GeWidgetActionResolver.findUniqueItem(root, itemId, "Select", "Choose");
		if (spec == null)
		{
			spec = GeWidgetActionResolver.findUniqueItem(root, itemId);
		}
		if (spec == null && !sellInventory)
		{
			Widget window = visible(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
			spec = GeWidgetActionResolver.findUniqueItem(window, itemId, "Select", "Choose");
		}
		return spec;
	}

	private Widget setupRoot()
	{
		Widget setup = visible(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		return setup != null ? setup : visible(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
	}

	private Widget visible(WidgetInfo info)
	{
		Widget widget = client.getWidget(info);
		return widget != null && !widget.isHidden() ? widget : null;
	}

	private GeReasonCode execute(GeWidgetActionSpec spec)
	{
		return execution.execute(spec);
	}
}
