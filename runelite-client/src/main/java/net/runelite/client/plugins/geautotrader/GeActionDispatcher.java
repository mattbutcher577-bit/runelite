package net.runelite.client.plugins.geautotrader;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
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
				return execute(slotAction(state, action.getSlot(), true, "Create Buy offer"));
			case OPEN_SELL:
				return execute(slotAction(state, action.getSlot(), true, "Create Sell offer"));
			case OPEN_OFFER:
				return execute(slotAction(state, action.getSlot(), false, "View offer"));
			case TYPE_ITEM_SEARCH:
				return promptInput.typeItemSearch(action.getItemName(), state);
			case SELECT_ITEM:
				return execute(findSearchResultAction(action.getItemId(), action.getItemName()));
			case SELECT_SELL_ITEM:
				return execute(findSellInventoryAction(action.getItemId()));
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
		if (state == null
			|| !GeSafetyPolicy.canUseSlot(targetSlot)
			|| visible(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER) == null)
		{
			return null;
		}

		GeObservedSlot observed = null;
		for (GeObservedSlot slot : state.getSlots())
		{
			if (slot != null && slot.getSlot() == targetSlot)
			{
				observed = slot;
				break;
			}
		}
		if (observed == null || observed.isEmpty() != empty)
		{
			return null;
		}

		Widget root = slotRoot(targetSlot);
		if (root == null || root.isHidden())
		{
			return null;
		}
		return GeWidgetActionResolver.findUnique(root, aliases);
	}

	private Widget slotRoot(int targetSlot)
	{
		switch (targetSlot)
		{
			case 1:
				return client.getWidget(InterfaceID.GeOffers.INDEX_0);
			case 2:
				return client.getWidget(InterfaceID.GeOffers.INDEX_1);
			case 3:
				return client.getWidget(InterfaceID.GeOffers.INDEX_2);
			default:
				return null;
		}
	}

	private GeWidgetActionSpec findSearchResultAction(int itemId, String itemName)
	{
		Widget container = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
		if (container == null || container.isHidden())
		{
			return null;
		}

		GeWidgetActionSpec direct = GeWidgetActionResolver.findUniqueItem(
			container, itemId, "Select", "Choose");
		if (direct != null)
		{
			return direct;
		}

		if (!GeWidgetActionResolver.findVisibleItemIds(container).contains(itemId))
		{
			return null;
		}
		return GeWidgetActionResolver.findUniqueNamed(
			container, itemName, "Select", "Choose");
	}

	private GeWidgetActionSpec findSellInventoryAction(int itemId)
	{
		Widget root = visible(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER);
		if (root == null)
		{
			return null;
		}
		GeWidgetActionSpec spec = GeWidgetActionResolver.findUniqueItem(root, itemId, "Offer", "Sell");
		return spec != null ? spec : GeWidgetActionResolver.findUniqueItem(root, itemId);
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
