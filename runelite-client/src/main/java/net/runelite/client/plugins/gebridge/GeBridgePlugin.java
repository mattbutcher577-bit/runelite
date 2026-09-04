package net.runelite.client.plugins.gebridge;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "GE State Bridge",
	description = "Publishes read-only Grand Exchange, inventory, interface, and client state to localhost",
	tags = {"grandexchange", "ge", "bridge", "developer"},
	enabledByDefault = true,
	loadInSafeMode = false
)
public class GeBridgePlugin extends Plugin
{
	static final int PORT = 17654;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Gson gson;

	private final AtomicReference<GeBridgeSnapshot> snapshot = new AtomicReference<>();
	private GeBridgeHttpServer httpServer;
	private boolean loggedInTickSeen;
	private long bridgeTick;

	@Override
	protected void startUp() throws Exception
	{
		loggedInTickSeen = false;
		bridgeTick = 0L;
		publishUnavailableSnapshot(client.getGameState());
		httpServer = new GeBridgeHttpServer(gson, snapshot::get, PORT);
		try
		{
			httpServer.start();
			log.info("GE state bridge listening on 127.0.0.1:{}", PORT);
		}
		catch (IOException ex)
		{
			log.error("Unable to bind GE state bridge on 127.0.0.1:{}", PORT, ex);
			throw ex;
		}

		clientThread.invoke(this::refreshSnapshotIfReady);
	}

	@Override
	protected void shutDown()
	{
		if (httpServer != null)
		{
			httpServer.stop();
			httpServer = null;
		}
		loggedInTickSeen = false;
		bridgeTick = 0L;
		publishUnavailableSnapshot(GameState.UNKNOWN);
		log.info("GE state bridge stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state != GameState.LOGGED_IN)
		{
			loggedInTickSeen = false;
			bridgeTick = 0L;
			publishUnavailableSnapshot(state);
			return;
		}

		// RuneLite emits initial EMPTY GE events while login is still settling.
		// Keep generatedAt=0 until the first full game tick so Python fails closed.
		loggedInTickSeen = false;
		bridgeTick = 0L;
		publishUnavailableSnapshot(state);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loggedInTickSeen = true;
			bridgeTick++;
			refreshSnapshotIfReady();
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		refreshSnapshotIfReady();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			refreshSnapshotIfReady();
		}
	}

	private void refreshSnapshotIfReady()
	{
		GameState gameState = client.getGameState();
		if (gameState != GameState.LOGGED_IN || !loggedInTickSeen)
		{
			publishUnavailableSnapshot(gameState);
			return;
		}

		ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INV);
		Item[] inventory = inventoryContainer == null ? new Item[0] : inventoryContainer.getItems();

		GeBridgeClientState clientState = readClientState(gameState);
		GeBridgePlayerState playerState = readPlayerState();
		GeBridgeInterfaceState interfaceState = readInterfaceState();
		GeBridgeGeState geState = readGeState(interfaceState);
		GeBridgeSafetyState safetyState = readSafetyState(playerState, interfaceState);

		snapshot.set(GeBridgeSnapshotBuilder.build(
			gameState,
			client.getGrandExchangeOffers(),
			inventory,
			System.currentTimeMillis(),
			bridgeTick,
			clientState,
			playerState,
			interfaceState,
			geState,
			safetyState
		));
	}

	private GeBridgeClientState readClientState(GameState gameState)
	{
		List<String> worldTypes = new ArrayList<>();
		for (WorldType worldType : client.getWorldType())
		{
			worldTypes.add(worldType.name());
		}
		Collections.sort(worldTypes);

		return new GeBridgeClientState(
			gameState == GameState.LOGGED_IN && loggedInTickSeen,
			client.getWorld(),
			worldTypes,
			client.getWorldType().contains(WorldType.MEMBERS),
			client.getCanvasWidth(),
			client.getCanvasHeight(),
			client.getViewportWidth(),
			client.getViewportHeight(),
			client.getViewportXOffset(),
			client.getViewportYOffset(),
			client.getTopLevelInterfaceId(),
			client.getFPS()
		);
	}

	private GeBridgePlayerState readPlayerState()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return GeBridgePlayerState.unavailable();
		}

		WorldPoint worldPoint = player.getWorldLocation();
		if (worldPoint == null)
		{
			return GeBridgePlayerState.unavailable();
		}

		return new GeBridgePlayerState(
			true,
			worldPoint.getX(),
			worldPoint.getY(),
			worldPoint.getPlane()
		);
	}

	private GeBridgeInterfaceState readInterfaceState()
	{
		boolean grandExchangeOpen = isVisible(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
		boolean grandExchangeOfferSetupOpen = isVisible(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
		boolean bankOpen = isVisible(WidgetInfo.BANK_CONTAINER);
		boolean worldMapOpen = isVisible(WidgetInfo.WORLD_MAP_VIEW);
		boolean dialogOpen =
			isVisible(WidgetInfo.DIALOG_NPC_TEXT)
				|| isVisible(WidgetInfo.DIALOG_PLAYER_TEXT)
				|| isVisible(WidgetInfo.DIALOG_OPTION)
				|| isVisible(WidgetInfo.DIALOG_SPRITE_TEXT)
				|| isVisible(WidgetInfo.DIALOG_DOUBLE_SPRITE_TEXT);
		boolean chatboxInputOpen = isVisible(WidgetInfo.CHATBOX_FULL_INPUT);

		return new GeBridgeInterfaceState(
			grandExchangeOpen,
			grandExchangeOfferSetupOpen,
			bankOpen,
			worldMapOpen,
			dialogOpen,
			chatboxInputOpen,
			client.isDraggingWidget()
		);
	}

	private GeBridgeGeState readGeState(GeBridgeInterfaceState interfaceState)
	{
		int offerSetupItemId = interfaceState.isGrandExchangeOfferSetupOpen()
			? client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)
			: -1;

		return new GeBridgeGeState(
			interfaceState.isGrandExchangeOpen(),
			interfaceState.isGrandExchangeOfferSetupOpen(),
			offerSetupItemId,
			boundsOf(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER),
			boundsOf(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER),
			boundsOf(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER)
		);
	}

	private GeBridgeSafetyState readSafetyState(
		GeBridgePlayerState playerState,
		GeBridgeInterfaceState interfaceState)
	{
		boolean bridgeReady =
			client.getGameState() == GameState.LOGGED_IN
				&& loggedInTickSeen
				&& playerState.isPresent();
		boolean modalBlocker =
			interfaceState.isBankOpen()
				|| interfaceState.isWorldMapOpen()
				|| interfaceState.isDialogOpen()
				|| interfaceState.isChatboxInputOpen()
				|| interfaceState.isDraggingWidget();
		boolean safeForMouseActions = bridgeReady && !modalBlocker;
		boolean safeForGeMouseActions = safeForMouseActions && interfaceState.isGrandExchangeOpen();

		return new GeBridgeSafetyState(
			bridgeReady,
			modalBlocker,
			safeForMouseActions,
			safeForGeMouseActions
		);
	}

	private boolean isVisible(WidgetInfo widgetInfo)
	{
		Widget widget = client.getWidget(widgetInfo);
		return widget != null && !widget.isHidden();
	}

	private GeBridgeBounds boundsOf(WidgetInfo widgetInfo)
	{
		Widget widget = client.getWidget(widgetInfo);
		if (widget == null || widget.isHidden())
		{
			return GeBridgeBounds.invalid();
		}
		return GeBridgeBounds.from(widget.getBounds());
	}

	private void publishUnavailableSnapshot(GameState gameState)
	{
		GeBridgeClientState clientState = new GeBridgeClientState(
			false,
			client.getWorld(),
			Collections.emptyList(),
			false,
			client.getCanvasWidth(),
			client.getCanvasHeight(),
			client.getViewportWidth(),
			client.getViewportHeight(),
			client.getViewportXOffset(),
			client.getViewportYOffset(),
			client.getTopLevelInterfaceId(),
			client.getFPS()
		);
		GeBridgeInterfaceState interfaceState = new GeBridgeInterfaceState(
			false, false, false, false, false, false, false);
		GeBridgeGeState geState = new GeBridgeGeState(
			false,
			false,
			-1,
			GeBridgeBounds.invalid(),
			GeBridgeBounds.invalid(),
			GeBridgeBounds.invalid()
		);
		GeBridgeSafetyState safetyState = new GeBridgeSafetyState(false, false, false, false);

		snapshot.set(GeBridgeSnapshotBuilder.build(
			gameState,
			null,
			new Item[0],
			0L,
			bridgeTick,
			clientState,
			GeBridgePlayerState.unavailable(),
			interfaceState,
			geState,
			safetyState
		));
	}
}
