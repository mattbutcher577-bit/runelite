package net.runelite.client.plugins.geautotrader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.util.Text;

public final class GeStateReader
{
	private static final int COINS_ITEM_ID = 995;
	private static final String BUY_QUANTITY_PROMPT = "How many do you wish to buy?";
	private static final String SELL_QUANTITY_PROMPT = "How many do you wish to sell?";
	private static final String PRICE_PROMPT = "Set a price for each item:";

	private final Client client;

	public GeStateReader(Client client)
	{
		this.client = client;
	}

	public GeObservedState read(boolean loginSettled)
	{
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		boolean membersWorld = client.getWorldType().contains(WorldType.MEMBERS);
		boolean geOpen = isVisible(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
		boolean setupOpen = isVisible(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		boolean offerDetailsVisible = isVisible(InterfaceID.GeOffers.DETAILS);
		boolean blockerActive = isVisible(WidgetInfo.BANK_CONTAINER)
			|| isVisible(WidgetInfo.WORLD_MAP_VIEW)
			|| isVisible(WidgetInfo.DIALOG_NPC_TEXT)
			|| isVisible(WidgetInfo.DIALOG_PLAYER_TEXT)
			|| isVisible(WidgetInfo.DIALOG_OPTION)
			|| isVisible(WidgetInfo.DIALOG_SPRITE_TEXT)
			|| isVisible(WidgetInfo.DIALOG_DOUBLE_SPRITE_TEXT)
			|| client.isDraggingWidget();

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		Map<Integer, Integer> inventoryCounts = inventoryCounts(inventory);
		long gp = inventoryCounts.getOrDefault(COINS_ITEM_ID, 0);

		int setupItemId = setupOpen ? client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) : -1;
		int setupQuantity = setupOpen ? client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) : 0;
		int setupPrice = setupOpen ? client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE) : 0;
		GeTradeSide setupSide = setupOpen
			? offerSide(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE))
			: GeTradeSide.UNKNOWN;
		GePromptMode promptMode = classifyPrompt(setupOpen, setupItemId, setupSide);

		return new GeObservedState(
			loggedIn,
			membersWorld,
			loginSettled,
			geOpen,
			blockerActive,
			client.getWorld(),
			gp,
			readSlots(client.getGrandExchangeOffers()),
			inventoryCounts,
			setupItemId,
			setupQuantity,
			setupPrice,
			setupSide,
			promptMode,
			readSearchResultItemIds(setupOpen, promptMode),
			offerDetailsVisible);
	}

	private List<GeObservedSlot> readSlots(GrandExchangeOffer[] offers)
	{
		List<GeObservedSlot> result = new ArrayList<>();
		if (offers == null)
		{
			return result;
		}
		for (int i = 0; i < offers.length; i++)
		{
			GrandExchangeOffer offer = offers[i];
			GrandExchangeOfferState state = offer == null || offer.getState() == null
				? GrandExchangeOfferState.EMPTY
				: offer.getState();
			result.add(new GeObservedSlot(
				i + 1,
				state.name(),
				offer == null ? -1 : offer.getItemId(),
				offer == null ? 0 : offer.getTotalQuantity(),
				offer == null ? 0 : offer.getQuantitySold(),
				offer == null ? 0 : offer.getPrice()));
		}
		return result;
	}

	private GePromptMode classifyPrompt(boolean setupOpen, int setupItemId, GeTradeSide setupSide)
	{
		if (!setupOpen)
		{
			return GePromptMode.NONE;
		}

		int messageLayerMode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		if (messageLayerMode == InputType.SEARCH.getType())
		{
			return GePromptMode.ITEM_SEARCH;
		}
		if (messageLayerMode == InputType.NONE.getType())
		{
			if (setupSide == GeTradeSide.BUY
				&& setupItemId < 0
				&& isVisible(WidgetInfo.CHATBOX_FULL_INPUT))
			{
				return GePromptMode.ITEM_SEARCH;
			}
			return GePromptMode.NONE;
		}

		Widget promptWidget = client.getWidget(WidgetInfo.CHATBOX_TITLE);
		String prompt = promptWidget == null ? null : Text.removeTags(promptWidget.getText());
		prompt = prompt == null ? "" : prompt.trim();
		if (BUY_QUANTITY_PROMPT.equals(prompt) || SELL_QUANTITY_PROMPT.equals(prompt))
		{
			return GePromptMode.QUANTITY;
		}
		if (PRICE_PROMPT.equals(prompt))
		{
			return GePromptMode.PRICE;
		}
		if (setupSide == GeTradeSide.BUY
			&& setupItemId < 0
			&& isVisible(WidgetInfo.CHATBOX_FULL_INPUT))
		{
			return GePromptMode.ITEM_SEARCH;
		}
		return GePromptMode.UNKNOWN;
	}

	private Set<Integer> readSearchResultItemIds(boolean setupOpen, GePromptMode promptMode)
	{
		if (!setupOpen || promptMode != GePromptMode.ITEM_SEARCH)
		{
			return java.util.Collections.emptySet();
		}
		Widget container = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
		if (container == null || container.isHidden())
		{
			return java.util.Collections.emptySet();
		}
		return GeWidgetActionResolver.findVisibleItemIds(container);
	}

	private boolean isVisible(WidgetInfo info)
	{
		Widget widget = client.getWidget(info);
		return widget != null && !widget.isHidden();
	}

	private boolean isVisible(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget != null && !widget.isHidden();
	}

	private static Map<Integer, Integer> inventoryCounts(ItemContainer inventory)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (inventory == null)
		{
			return counts;
		}
		Item[] items = inventory.getItems();
		if (items == null)
		{
			return counts;
		}
		for (Item item : items)
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			counts.merge(item.getId(), item.getQuantity(), Integer::sum);
		}
		return counts;
	}

	private static GeTradeSide offerSide(int value)
	{
		if (value == 0)
		{
			return GeTradeSide.BUY;
		}
		if (value == 1)
		{
			return GeTradeSide.SELL;
		}
		return GeTradeSide.UNKNOWN;
	}
}
