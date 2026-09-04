package net.runelite.client.plugins.gebridge;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "GE State Bridge",
	description = "Publishes read-only Grand Exchange and inventory state to a localhost JSON endpoint",
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

	@Override
	protected void startUp() throws Exception
	{
		loggedInTickSeen = false;
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
			publishUnavailableSnapshot(state);
			return;
		}

		// RuneLite emits initial EMPTY GE events while login is still settling.
		// Keep generatedAt=0 until the first full game tick so Python fails closed.
		loggedInTickSeen = false;
		publishUnavailableSnapshot(state);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loggedInTickSeen = true;
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
		snapshot.set(GeBridgeSnapshotBuilder.build(
			gameState,
			client.getGrandExchangeOffers(),
			inventory,
			System.currentTimeMillis()
		));
	}

	private void publishUnavailableSnapshot(GameState gameState)
	{
		snapshot.set(GeBridgeSnapshotBuilder.build(
			gameState,
			null,
			new Item[0],
			0L
		));
	}
}
