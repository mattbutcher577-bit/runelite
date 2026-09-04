package net.runelite.client.plugins.geautotrader;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class GeTradeStateMachine
{
	private static final Duration BUY_TIMEOUT = Duration.ofMinutes(20);

	private final GeAutoTraderConfig config;
	private final GeLimitLedger limitLedger;
	private final GeTradeLedger tradeLedger;
	private final Supplier<GeMarketSnapshot> marketSupplier;
	private final BooleanSupplier enabled;
	private final BooleanSupplier stopped;
	private final Map<Integer, SlotContext> contexts = new HashMap<>();
	private long obligationSequence;
	private GeReasonCode lastReason = GeReasonCode.OK;

	public GeTradeStateMachine(
		GeAutoTraderConfig config,
		GeLimitLedger limitLedger,
		GeTradeLedger tradeLedger,
		Supplier<GeMarketSnapshot> marketSupplier,
		BooleanSupplier enabled,
		BooleanSupplier stopped)
	{
		this.config = config;
		this.limitLedger = limitLedger;
		this.tradeLedger = tradeLedger;
		this.marketSupplier = marketSupplier;
		this.enabled = enabled;
		this.stopped = stopped;
		for (int slot = 1; slot <= 3; slot++)
		{
			contexts.put(slot, new SlotContext(slot));
		}
	}

	public GePlannedAction onTick(GeObservedState state, Instant now)
	{
		if (now == null)
		{
			lastReason = GeReasonCode.EXECUTION_REJECTED;
			return GePlannedAction.none();
		}

		GeReasonCode global = GeSafetyPolicy.evaluateGlobal(
			state,
			enabled != null && enabled.getAsBoolean(),
			stopped != null && stopped.getAsBoolean());
		if (global != GeReasonCode.OK)
		{
			lastReason = global;
			return GePlannedAction.none();
		}

		for (int slot = 1; slot <= 3; slot++)
		{
			GePlannedAction action = step(contexts.get(slot), state, now);
			if (action.getType() != GePlannedActionType.NONE)
			{
				lastReason = GeReasonCode.OK;
				return action;
			}
		}
		return GePlannedAction.none();
	}

	public GeTradePhase getPhase(int slot)
	{
		SlotContext context = contexts.get(slot);
		return context == null ? GeTradePhase.IDLE : context.phase;
	}

	public GeReasonCode getLastReason()
	{
		return lastReason;
	}

	public GeCandidate getCurrentCandidate(int slot)
	{
		SlotContext context = contexts.get(slot);
		return context == null ? null : context.candidate;
	}

	private GePlannedAction step(SlotContext context, GeObservedState state, Instant now)
	{
		GeObservedSlot observed = findSlot(state, context.slot);
		if (observed == null)
		{
			lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
			return GePlannedAction.none();
		}

		switch (context.phase)
		{
			case IDLE:
				return idle(context, state, observed, now);
			case WAIT_BUY_SETUP:
				if (state.getPromptMode() == GePromptMode.ITEM_SEARCH && state.getSetupSide() == GeTradeSide.BUY)
				{
					context.phase = GeTradePhase.WAIT_SEARCH_RESULTS;
					return action(context, GePlannedActionType.TYPE_ITEM_SEARCH);
				}
				return GePlannedAction.none();
			case WAIT_SEARCH_RESULTS:
				if (state.getSetupItemId() == context.candidate.getItemId())
				{
					context.phase = GeTradePhase.WAIT_ITEM_SELECTED;
					return step(context, state, now);
				}
				if (state.getPromptMode() == GePromptMode.NONE)
				{
					context.phase = GeTradePhase.WAIT_ITEM_SELECTED;
					return action(context, GePlannedActionType.SELECT_ITEM);
				}
				return GePlannedAction.none();
			case WAIT_ITEM_SELECTED:
				if (state.getSetupItemId() < 0)
				{
					return GePlannedAction.none();
				}
				if (state.getSetupItemId() != context.candidate.getItemId())
				{
					lastReason = GeReasonCode.SETUP_ITEM_MISMATCH;
					return GePlannedAction.none();
				}
				context.phase = GeTradePhase.WAIT_QUANTITY_PROMPT;
				return action(context, GePlannedActionType.OPEN_QUANTITY);
			case WAIT_QUANTITY_PROMPT:
				if (state.getPromptMode() == GePromptMode.QUANTITY)
				{
					context.phase = GeTradePhase.WAIT_QUANTITY_VALUE;
					return action(context, GePlannedActionType.TYPE_QUANTITY);
				}
				return GePlannedAction.none();
			case WAIT_QUANTITY_VALUE:
				if (state.getSetupQuantity() == context.candidate.getQuantity())
				{
					context.phase = GeTradePhase.WAIT_PRICE_PROMPT;
					return action(context, GePlannedActionType.OPEN_PRICE);
				}
				if (state.getSetupQuantity() > 0 && state.getPromptMode() == GePromptMode.NONE)
				{
					lastReason = GeReasonCode.SETUP_QUANTITY_MISMATCH;
				}
				return GePlannedAction.none();
			case WAIT_PRICE_PROMPT:
				if (state.getPromptMode() == GePromptMode.PRICE)
				{
					context.phase = GeTradePhase.WAIT_PRICE_VALUE;
					return action(context, GePlannedActionType.TYPE_PRICE);
				}
				return GePlannedAction.none();
			case WAIT_PRICE_VALUE:
				if (!setupMatches(state, context.candidate, GeTradeSide.BUY))
				{
					return GePlannedAction.none();
				}
				context.phase = GeTradePhase.WAIT_BUY_SLOT;
				return action(context, GePlannedActionType.CONFIRM);
			case WAIT_BUY_SLOT:
				if (isBuyPlaced(observed, context.candidate))
				{
					tradeLedger.markPlaced(context.obligationId, now);
					tradeLedger.markFilled(context.obligationId, observed.getFilledQuantity());
					context.phase = GeTradePhase.MONITOR_BUY;
				}
				else if (!observed.isEmpty())
				{
					lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
				}
				return GePlannedAction.none();
			case MONITOR_BUY:
				return monitorBuy(context, observed, now);
			default:
				return GePlannedAction.none();
		}
	}

	private GePlannedAction idle(SlotContext context, GeObservedState state, GeObservedSlot observed, Instant now)
	{
		if (!observed.isEmpty())
		{
			return GePlannedAction.none();
		}
		GeMarketSnapshot market = marketSupplier == null ? null : marketSupplier.get();
		if (market == null)
		{
			lastReason = GeReasonCode.MARKET_DATA_UNAVAILABLE;
			return GePlannedAction.none();
		}
		Duration maxAge = Duration.ofSeconds(Math.max(15L, (long) config.marketRefreshSeconds() * 3L));
		if (market.isStale(now, maxAge))
		{
			lastReason = GeReasonCode.MARKET_DATA_STALE;
			return GePlannedAction.none();
		}

		long availableGp = Math.max(0L, state.getGp() - tradeLedger.reservedGp());
		List<GeCandidate> candidates = GeOpportunitySelector.select(
			market, availableGp, limitLedger, tradeLedger, config, now);
		if (candidates.isEmpty())
		{
			return GePlannedAction.none();
		}

		GeCandidate candidate = candidates.get(0);
		String obligationId = "v6-buy-" + (++obligationSequence);
		tradeLedger.reserveBuy(
			obligationId,
			context.slot,
			candidate.getItemId(),
			candidate.getName(),
			candidate.getQuantity(),
			candidate.getBuyPrice());
		context.candidate = candidate;
		context.obligationId = obligationId;
		context.phase = GeTradePhase.WAIT_BUY_SETUP;
		return action(context, GePlannedActionType.OPEN_BUY);
	}

	private GePlannedAction monitorBuy(SlotContext context, GeObservedSlot observed, Instant now)
	{
		if (observed.getItemId() == context.candidate.getItemId())
		{
			tradeLedger.markFilled(context.obligationId, observed.getFilledQuantity());
		}
		if ("BOUGHT".equalsIgnoreCase(observed.getState())
			|| "CANCELLED_BUY".equalsIgnoreCase(observed.getState()))
		{
			context.phase = GeTradePhase.WAIT_BUY_COLLECT_READY;
			return action(context, GePlannedActionType.OPEN_OFFER);
		}

		GeTradeObligation obligation = tradeLedger.get(context.obligationId);
		if (obligation != null && obligation.getPlacedAt() != null
			&& obligation.getAbortCount() == 0
			&& !now.isBefore(obligation.getPlacedAt().plus(BUY_TIMEOUT)))
		{
			context.phase = GeTradePhase.WAIT_ABORT_READY;
			return action(context, GePlannedActionType.OPEN_OFFER);
		}
		return GePlannedAction.none();
	}

	private GePlannedAction action(SlotContext context, GePlannedActionType type)
	{
		GeCandidate candidate = context.candidate;
		return GePlannedAction.of(
			type,
			context.slot,
			candidate == null ? -1 : candidate.getItemId(),
			candidate == null ? "" : candidate.getName(),
			candidate == null ? 0 : candidate.getQuantity(),
			candidate == null ? 0 : candidate.getBuyPrice(),
			context.obligationId);
	}

	private boolean setupMatches(GeObservedState state, GeCandidate candidate, GeTradeSide side)
	{
		if (state.getSetupItemId() != candidate.getItemId())
		{
			lastReason = GeReasonCode.SETUP_ITEM_MISMATCH;
			return false;
		}
		if (state.getSetupQuantity() != candidate.getQuantity())
		{
			lastReason = GeReasonCode.SETUP_QUANTITY_MISMATCH;
			return false;
		}
		if (state.getSetupPrice() != candidate.getBuyPrice())
		{
			lastReason = GeReasonCode.SETUP_PRICE_MISMATCH;
			return false;
		}
		if (state.getSetupSide() != side)
		{
			lastReason = GeReasonCode.SETUP_SIDE_MISMATCH;
			return false;
		}
		return true;
	}

	private static boolean isBuyPlaced(GeObservedSlot observed, GeCandidate candidate)
	{
		String state = observed.getState();
		return ("BUYING".equalsIgnoreCase(state) || "BOUGHT".equalsIgnoreCase(state))
			&& observed.getItemId() == candidate.getItemId()
			&& observed.getTotalQuantity() == candidate.getQuantity()
			&& observed.getPrice() == candidate.getBuyPrice();
	}

	private static GeObservedSlot findSlot(GeObservedState state, int slot)
	{
		if (state == null)
		{
			return null;
		}
		for (GeObservedSlot observed : state.getSlots())
		{
			if (observed != null && observed.getSlot() == slot)
			{
				return observed;
			}
		}
		return null;
	}

	private static final class SlotContext
	{
		private final int slot;
		private GeTradePhase phase = GeTradePhase.IDLE;
		private GeCandidate candidate;
		private String obligationId = "";

		private SlotContext(int slot)
		{
			this.slot = slot;
		}
	}
}
