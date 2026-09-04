package net.runelite.client.plugins.geautotrader;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "GE Auto-Trader V6",
	description = "Private-fork automatic F2P Grand Exchange trader",
	tags = {"grand exchange", "ge", "trading", "developer"},
	enabledByDefault = false,
	loadInSafeMode = false
)
public class GeAutoTraderPlugin extends Plugin implements KeyListener
{
	static final String CONFIG_GROUP = "geautotraderv6";

	@Inject
	private Client client;

	@Inject
	private GeAutoTraderConfig config;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GeAutoTraderOverlay overlay;

	private final AtomicBoolean stopped = new AtomicBoolean();
	private GeStateReader stateReader;
	private GeMarketService marketService;
	private GeTradeLedger tradeLedger;
	private GeLimitLedger limitLedger;
	private GeTradeStateMachine stateMachine;
	private GeActionDispatcher dispatcher;
	private GeObservedState lastState;
	private GePlannedAction lastAction = GePlannedAction.none();
	private GeReasonCode lastReason = GeReasonCode.DISABLED;
	private int loggedInTicks;
	private boolean manualRestartAllowed;
	private boolean restartArmed;
	private boolean restartRequested;

	@Provides
	GeAutoTraderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GeAutoTraderConfig.class);
	}

	@Override
	protected void startUp()
	{
		stopped.set(false);
		manualRestartAllowed = false;
		restartArmed = false;
		restartRequested = false;
		loggedInTicks = 0;
		tradeLedger = new GeTradeLedger();
		limitLedger = new GeLimitLedger();
		stateReader = new GeStateReader(client);
		marketService = new GeMarketService(
			httpClient,
			gson,
			Duration.ofSeconds(Math.max(5, config.marketRefreshSeconds())));
		GeExecutionService execution = new GeExecutionService(client, stopped::get);
		GePromptInputService promptInput = new GePromptInputService(client.getCanvas(), stopped::get);
		dispatcher = new GeActionDispatcher(client, execution, promptInput);
		stateMachine = new GeTradeStateMachine(
			config,
			limitLedger,
			tradeLedger,
			marketService::snapshot,
			config::enabled,
			stopped::get);
		keyManager.registerKeyListener(this);
		overlayManager.add(overlay);
		marketService.refreshAsync();
	}

	@Override
	protected void shutDown()
	{
		stopped.set(true);
		manualRestartAllowed = false;
		restartArmed = false;
		restartRequested = false;
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(overlay);
		if (marketService != null)
		{
			marketService.close();
			marketService = null;
		}
		stateMachine = null;
		dispatcher = null;
		stateReader = null;
		lastState = null;
		lastAction = GePlannedAction.none();
		lastReason = GeReasonCode.DISABLED;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		loggedInTicks = 0;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event == null
			|| !CONFIG_GROUP.equals(event.getGroup())
			|| !"enabled".equals(event.getKey()))
		{
			return;
		}

		if (!stopped.get() || !manualRestartAllowed)
		{
			restartArmed = false;
			restartRequested = false;
			return;
		}

		boolean enabled = Boolean.parseBoolean(event.getNewValue());
		if (!enabled)
		{
			restartArmed = true;
			restartRequested = false;
		}
		else if (restartArmed)
		{
			restartRequested = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (stateReader == null || stateMachine == null || dispatcher == null)
		{
			return;
		}

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loggedInTicks++;
		}
		else
		{
			loggedInTicks = 0;
		}

		lastState = stateReader.read(loggedInTicks >= 2);
		if (marketService != null)
		{
			marketService.refreshAsync();
		}

		if (restartRequested && config.enabled() && canCompleteManualRestart(lastState))
		{
			stateMachine.recoverAbandonedBuySetups(lastState);
			stopped.set(false);
			manualRestartAllowed = false;
			restartArmed = false;
			restartRequested = false;
			lastAction = GePlannedAction.none();
			lastReason = GeReasonCode.OK;
		}

		GePlannedAction action = stateMachine.onTick(lastState, Instant.now());
		lastAction = action;
		lastReason = stateMachine.getLastReason();
		if (action.getType() == GePlannedActionType.NONE)
		{
			return;
		}

		GeReasonCode executionResult = dispatcher.dispatch(action, lastState);
		if (executionResult != GeReasonCode.OK)
		{
			lastReason = executionResult;
			// Fail closed. The state machine has already advanced past the emitted action;
			// stopping prevents it from acting on an unproved transition.
			stopForExecutionFailure();
		}
	}

	private static boolean canCompleteManualRestart(GeObservedState state)
	{
		return state != null
			&& state.isLoggedIn()
			&& state.isLoginSettled()
			&& state.isGeOpen()
			&& !state.isBlockerActive()
			&& state.getSetupSide() == GeTradeSide.UNKNOWN
			&& state.getPromptMode() == GePromptMode.NONE;
	}

	void stopForExecutionFailure()
	{
		stopped.set(true);
		manualRestartAllowed = false;
		restartArmed = false;
		restartRequested = false;
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (event != null && event.getKeyCode() == KeyEvent.VK_F8)
		{
			stopped.set(true);
			manualRestartAllowed = true;
			restartArmed = false;
			restartRequested = false;
			lastReason = GeReasonCode.STOPPED_F8;
			event.consume();
		}
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	boolean isStopped()
	{
		return stopped.get();
	}

	boolean isManualRestartAllowed()
	{
		return manualRestartAllowed;
	}

	boolean isRestartRequested()
	{
		return restartRequested;
	}

	String getStatusText()
	{
		if (stopped.get())
		{
			return "STOPPED";
		}
		if (config == null || !config.enabled())
		{
			return "PAUSED";
		}
		return lastReason == GeReasonCode.OK ? "RUNNING" : "PAUSED";
	}

	String getMarketStatusText()
	{
		if (marketService == null)
		{
			return "OFF";
		}
		GeMarketSnapshot snapshot = marketService.snapshot();
		if (snapshot != null)
		{
			return "READY " + snapshot.getItems().size();
		}
		if (!marketService.getLastError().isEmpty())
		{
			return "ERROR";
		}
		return marketService.isRefreshing() ? "LOADING" : "WAITING";
	}

	String getMarketErrorText()
	{
		if (marketService == null)
		{
			return "";
		}
		String value = marketService.getLastError();
		if (value.length() > 72)
		{
			return value.substring(0, 69) + "...";
		}
		return value;
	}

	GeReasonCode getLastReason()
	{
		return lastReason;
	}

	GeObservedState getLastState()
	{
		return lastState;
	}

	long getReservedGp()
	{
		return tradeLedger == null ? 0L : tradeLedger.reservedGp();
	}

	GeTradePhase getPhase(int slot)
	{
		return stateMachine == null ? GeTradePhase.IDLE : stateMachine.getPhase(slot);
	}

	GeCandidate getCandidate(int slot)
	{
		return stateMachine == null ? null : stateMachine.getCurrentCandidate(slot);
	}

	GePlannedAction getLastAction()
	{
		return lastAction;
	}
}
