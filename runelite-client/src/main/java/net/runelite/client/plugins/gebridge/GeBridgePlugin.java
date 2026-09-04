package net.runelite.client.plugins.gebridge;

import com.google.gson.Gson;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "GE State Bridge",
	description = "Publishes read-only Grand Exchange, inventory, interface, client, input, search, and mouse state to localhost",
	tags = {"grandexchange", "ge", "bridge", "developer"},
	enabledByDefault = true,
	loadInSafeMode = false
)
public class GeBridgePlugin extends Plugin
{
	static final int PORT = 17654;
	private static final long INPUT_REFRESH_THROTTLE_NANOS = 75_000_000L;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Gson gson;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientUI clientUI;

	@Inject
	private ItemManager itemManager;

	private final AtomicReference<GeBridgeSnapshot> snapshot = new AtomicReference<>();
	private final AtomicLong lastInputRefreshNanos = new AtomicLong();
	private GeBridgeHttpServer httpServer;
	private GeBridgeInputTracker inputTracker;
	private GeBridgeMouseTracker mouseTracker;
	private boolean loggedInTickSeen;
	private long bridgeTick;
	private long snapshotSeq;
	private String bridgeInstanceId = "";

	@Override
	protected void startUp() throws Exception
	{
		loggedInTickSeen = false;
		bridgeTick = 0L;
		snapshotSeq = 0L;
		bridgeInstanceId = UUID.randomUUID().toString();
		lastInputRefreshNanos.set(0L);
		inputTracker = new GeBridgeInputTracker(this::requestInputRefresh);
		mouseTracker = new GeBridgeMouseTracker(this::requestInputRefresh);
		mouseManager.registerMouseListener(inputTracker);
		mouseManager.registerMouseWheelListener(inputTracker);
		mouseManager.registerMouseListener(mouseTracker);
		mouseManager.registerMouseWheelListener(mouseTracker);
		keyManager.registerKeyListener(inputTracker);

		publishUnavailableSnapshot(client.getGameState());
		httpServer = new GeBridgeHttpServer(gson, snapshot::get, PORT);
		try
		{
			httpServer.start();
			log.info("GE state bridge listening on 127.0.0.1:{}", PORT);
		}
		catch (IOException ex)
		{
			unregisterTrackers();
			log.error("Unable to bind GE state bridge on 127.0.0.1:{}", PORT, ex);
			throw ex;
		}

		clientThread.invoke(this::refreshSnapshotIfReady);
	}

	@Override
	protected void shutDown()
	{
		unregisterTrackers();
		if (httpServer != null)
		{
			httpServer.stop();
			httpServer = null;
		}
		loggedInTickSeen = false;
		bridgeTick = 0L;
		lastInputRefreshNanos.set(0L);
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

	private void requestInputRefresh()
	{
		long now = System.nanoTime();
		long previous = lastInputRefreshNanos.get();
		if (now - previous < INPUT_REFRESH_THROTTLE_NANOS)
		{
			return;
		}
		if (lastInputRefreshNanos.compareAndSet(previous, now))
		{
			clientThread.invokeLater(this::refreshSnapshotIfReady);
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
		long nowEpochMs = System.currentTimeMillis();
		long seq = nextSnapshotSeq();

		GeBridgeClientState clientState = readClientState(gameState);
		GeBridgePlayerState playerState = readPlayerState();
		GeBridgeInterfaceState interfaceState = readInterfaceState();
		GeBridgeGeState geState = readGeState(interfaceState);
		GeBridgeSafetyState safetyState = readSafetyState(playerState, interfaceState);
		GeBridgeInputState inputState = currentInputState(nowEpochMs);
		GeBridgeSearchState searchState = readSearchState(interfaceState);
		GeBridgeMouseState mouseState = readMouseState(seq);
		GeBridgeLiveGeSections liveGeSections = readLiveGeSections(interfaceState);

		snapshot.set(GeBridgeSnapshotBuilder.build(
			gameState,
			client.getGrandExchangeOffers(),
			inventory,
			nowEpochMs,
			bridgeTick,
			bridgeInstanceId,
			seq,
			clientState,
			playerState,
			interfaceState,
			geState,
			safetyState,
			inputState,
			searchState,
			mouseState,
			liveGeSections.getGeInput(),
			liveGeSections.getGeActions(),
			liveGeSections.getGeInventory()
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

		Point canvasScreenPoint = canvasScreenPoint();
		boolean canvasScreenPositionValid = canvasScreenPoint != null;
		int canvasScreenX = canvasScreenPositionValid ? canvasScreenPoint.x : -1;
		int canvasScreenY = canvasScreenPositionValid ? canvasScreenPoint.y : -1;

		Dimension realDimensions = client.getRealDimensions();
		Dimension stretchedDimensions = client.getStretchedDimensions();
		int realWidth = dimensionWidth(realDimensions);
		int realHeight = dimensionHeight(realDimensions);
		int stretchedWidth = dimensionWidth(stretchedDimensions);
		int stretchedHeight = dimensionHeight(stretchedDimensions);

		return new GeBridgeClientState(
			gameState == GameState.LOGGED_IN && loggedInTickSeen,
			client.getWorld(),
			worldTypes,
			client.getWorldType().contains(WorldType.MEMBERS),
			client.getCanvasWidth(),
			client.getCanvasHeight(),
			canvasScreenX,
			canvasScreenY,
			canvasScreenPositionValid,
			realWidth,
			realHeight,
			stretchedWidth,
			stretchedHeight,
			client.isStretchedEnabled(),
			client.getViewportWidth(),
			client.getViewportHeight(),
			client.getViewportXOffset(),
			client.getViewportYOffset(),
			client.getTopLevelInterfaceId(),
			client.getFPS()
		);
	}

	private static int dimensionWidth(Dimension dimension)
	{
		return dimension == null || dimension.width <= 0 ? -1 : dimension.width;
	}

	private static int dimensionHeight(Dimension dimension)
	{
		return dimension == null || dimension.height <= 0 ? -1 : dimension.height;
	}

	private Point canvasScreenPoint()
	{
		Canvas canvas = client.getCanvas();
		if (canvas == null || !canvas.isShowing())
		{
			return null;
		}

		try
		{
			return canvas.getLocationOnScreen();
		}
		catch (IllegalComponentStateException ex)
		{
			return null;
		}
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
		return new GeBridgePlayerState(true, worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane());
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

	private GeBridgeSearchState readSearchState(GeBridgeInterfaceState interfaceState)
	{
		if (!interfaceState.isGrandExchangeOfferSetupOpen() || !interfaceState.isChatboxInputOpen())
		{
			return GeBridgeSearchState.closed();
		}

		Widget container = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
		if (container == null || container.isHidden())
		{
			return GeBridgeSearchState.closed();
		}

		Widget[] children = container.getDynamicChildren();
		if (children == null || children.length < 3)
		{
			return GeBridgeSearchState.closed();
		}

		List<GeBridgeSearchResult> results = new ArrayList<>();
		for (int offset = 0, index = 0; offset + 2 < children.length; offset += 3, index++)
		{
			Widget icon = children[offset];
			Widget nameWidget = children[offset + 1];
			if (nameWidget == null || nameWidget.isHidden())
			{
				continue;
			}
			String name = Text.removeTags(nameWidget.getText());
			name = name == null ? "" : name.trim();
			int itemId = icon == null ? -1 : icon.getItemId();
			if (name.isEmpty() || itemId <= 0)
			{
				continue;
			}
			results.add(new GeBridgeSearchResult(
				index,
				itemId,
				name,
				icon == null ? GeBridgeBounds.invalid() : GeBridgeBounds.from(icon.getBounds()),
				GeBridgeBounds.from(nameWidget.getBounds())
			));
		}

		if (results.isEmpty())
		{
			return GeBridgeSearchState.closed();
		}

		return new GeBridgeSearchState(true, bridgeTick, results);
	}

	private GeBridgeLiveGeSections readLiveGeSections(GeBridgeInterfaceState interfaceState)
	{
		return GeBridgeLiveGeSections.read(
			interfaceState.isGrandExchangeOfferSetupOpen(),
			client.getVarcIntValue(VarClientID.MESLAYERMODE),
			client.getWidget(WidgetInfo.CHATBOX_TITLE),
			client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT),
			client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER),
			client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER),
			client.getWidget(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER),
			itemManager,
			bridgeTick);
	}

	private GeBridgeMouseState readMouseState(long seq)
	{
		if (mouseTracker == null)
		{
			return GeBridgeMouseState.unavailable();
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int x = mouse == null ? -1 : mouse.getX();
		int y = mouse == null ? -1 : mouse.getY();
		boolean inside = x >= 0 && y >= 0 && x < client.getCanvasWidth() && y < client.getCanvasHeight();
		Canvas canvas = client.getCanvas();
		boolean canvasFocused = canvas != null && canvas.isFocusOwner();
		boolean clientWindowFocused = clientUI != null && clientUI.isFocused();

		return mouseTracker.snapshot(
			bridgeTick,
			seq,
			x,
			y,
			inside,
			client.getMouseCurrentButton(),
			client.isDraggingWidget(),
			client.getMouseIdleTicks(),
			client.getMouseLastPressedMillis(),
			canvasFocused,
			clientWindowFocused);
	}

	private GeBridgeSafetyState readSafetyState(
		GeBridgePlayerState playerState,
		GeBridgeInterfaceState interfaceState)
	{
		boolean bridgeReady = client.getGameState() == GameState.LOGGED_IN
			&& loggedInTickSeen
			&& playerState.isPresent();
		return GeBridgeSafetyPolicy.evaluate(bridgeReady, interfaceState);
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

	private GeBridgeInputState currentInputState(long nowEpochMs)
	{
		return inputTracker == null ? emptyInputState() : inputTracker.snapshot(nowEpochMs);
	}

	private static GeBridgeInputState emptyInputState()
	{
		return new GeBridgeInputState(
			0L, 0L, 0L, 0L, 0L, 0L, 0L,
			-1, -1, false, 0, 0, 0, "", -1L);
	}

	private void unregisterTrackers()
	{
		if (inputTracker != null)
		{
			mouseManager.unregisterMouseListener(inputTracker);
			mouseManager.unregisterMouseWheelListener(inputTracker);
			keyManager.unregisterKeyListener(inputTracker);
			inputTracker = null;
		}
		if (mouseTracker != null)
		{
			mouseManager.unregisterMouseListener(mouseTracker);
			mouseManager.unregisterMouseWheelListener(mouseTracker);
			mouseTracker = null;
		}
	}

	private long nextSnapshotSeq()
	{
		return ++snapshotSeq;
	}

	private void publishUnavailableSnapshot(GameState gameState)
	{
		GeBridgeClientState clientState = readClientState(gameState);
		GeBridgeInterfaceState interfaceState = new GeBridgeInterfaceState(
			false, false, false, false, false, false, false);
		GeBridgeGeState geState = new GeBridgeGeState(
			false, false, -1,
			GeBridgeBounds.invalid(), GeBridgeBounds.invalid(), GeBridgeBounds.invalid());
		GeBridgeSafetyState safetyState = new GeBridgeSafetyState(false, false, false, false);
		long seq = nextSnapshotSeq();

		snapshot.set(GeBridgeSnapshotBuilder.build(
			gameState,
			null,
			new Item[0],
			0L,
			bridgeTick,
			bridgeInstanceId,
			seq,
			clientState,
			GeBridgePlayerState.unavailable(),
			interfaceState,
			geState,
			safetyState,
			currentInputState(System.currentTimeMillis()),
			GeBridgeSearchState.closed(),
			GeBridgeMouseState.unavailable(),
			GeBridgeGeInputState.none(bridgeTick),
			GeBridgeGeActionState.unavailable(bridgeTick),
			GeBridgeGeInventoryState.closed(bridgeTick)
		));
	}
}
