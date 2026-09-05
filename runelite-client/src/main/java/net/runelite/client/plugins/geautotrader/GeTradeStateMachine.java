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
	private static final Duration UI_PROOF_TIMEOUT = Duration.ofSeconds(10);

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

		lastReason = GeReasonCode.OK;

		for (int slot = 1; slot <= 3; slot++)
		{
			SlotContext context = contexts.get(slot);
			GeTradePhase beforePhase = context.phase;
			GePlannedAction action = step(context, state, now);
			if (context.phase != beforePhase)
			{
				context.phaseEnteredAt = now;
			}
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

	public GePlannedActionType getPendingAction(int slot)
	{
		SlotContext context = contexts.get(slot);
		return context == null || context.pending == null
			? GePlannedActionType.NONE : context.pending.getType();
	}

	public boolean recordExecutionResult(
		GePlannedAction action,
		GeReasonCode result,
		Instant now)
	{
		if (action == null || action.getType() == GePlannedActionType.NONE)
		{
			return false;
		}
		GeReasonCode safeResult = result == null ? GeReasonCode.EXECUTION_REJECTED : result;
		SlotContext context = contexts.get(action.getSlot());
		if (context != null && context.pending != null && context.pending.matches(action.getType()))
		{
			context.pending.recordResult(safeResult);
			if (safeResult == GeReasonCode.OK)
			{
				lastReason = GeReasonCode.OK;
				return false;
			}
			lastReason = safeResult;
			if (safeResult == GeReasonCode.EXECUTION_TARGET_UNAVAILABLE)
			{
				return context.pending.isTargetUnavailableExpired(now);
			}
			return true;
		}

		if (safeResult == GeReasonCode.OK)
		{
			return false;
		}
		lastReason = safeResult;
		return true;
	}

	int recoverAbandonedBuySetups(GeObservedState state)
	{
		int recovered = 0;
		for (int slot = 1; slot <= 3; slot++)
		{
			SlotContext context = contexts.get(slot);
			GeObservedSlot observed = findSlot(state, slot);
			if (context == null
				|| observed == null
				|| !observed.isEmpty()
				|| context.side != GeTradeSide.BUY
				|| !isPrePlacementBuyPhase(context.phase))
			{
				continue;
			}

			tradeLedger.remove(context.obligationId);
			context.reset();
			recovered++;
		}
		return recovered;
	}

	private static boolean isPrePlacementBuyPhase(GeTradePhase phase)
	{
		switch (phase)
		{
			case WAIT_BUY_SETUP:
			case WAIT_SEARCH_RESULTS:
			case WAIT_ITEM_SELECTED:
			case WAIT_QUANTITY_PROMPT:
			case WAIT_QUANTITY_VALUE:
			case WAIT_PRICE_PROMPT:
			case WAIT_PRICE_VALUE:
				return true;
			default:
				return false;
		}
	}

	private static boolean isSetupWorkflowPhase(GeTradePhase phase)
	{
		switch (phase)
		{
			case WAIT_BUY_SETUP:
			case WAIT_SEARCH_RESULTS:
			case WAIT_ITEM_SELECTED:
			case WAIT_QUANTITY_PROMPT:
			case WAIT_QUANTITY_VALUE:
			case WAIT_PRICE_PROMPT:
			case WAIT_PRICE_VALUE:
			case WAIT_BUY_SLOT:
			case WAIT_ABORT_READY:
			case WAIT_ABORT_RESULT:
			case WAIT_BUY_COLLECT_READY:
			case WAIT_BUY_COLLECT_RESULT:
			case WAIT_SELL_SETUP:
			case WAIT_SELL_ITEM_SELECTED:
			case WAIT_SELL_QUANTITY_PROMPT:
			case WAIT_SELL_QUANTITY_VALUE:
			case WAIT_SELL_PRICE_PROMPT:
			case WAIT_SELL_PRICE_VALUE:
			case WAIT_SELL_SLOT:
			case WAIT_SELL_COLLECT_READY:
			case WAIT_SELL_COLLECT_RESULT:
				return true;
			default:
				return false;
		}
	}

	private static boolean isUiProofPhase(GeTradePhase phase)
	{
		return isSetupWorkflowPhase(phase);
	}

	private static boolean isUiProofTimedOut(SlotContext context, Instant now)
	{
		return isUiProofPhase(context.phase)
			&& context.phaseEnteredAt != null
			&& now.isAfter(context.phaseEnteredAt.plus(UI_PROOF_TIMEOUT));
	}

	private static boolean hasCompletedAbortResult(SlotContext context, GeObservedSlot observed)
	{
		if (context.phase != GeTradePhase.WAIT_ABORT_RESULT || observed == null)
		{
			return false;
		}
		String state = observed.getState();
		return "CANCELLED_BUY".equalsIgnoreCase(state) || "BOUGHT".equalsIgnoreCase(state);
	}

	private boolean anotherSetupWorkflowInProgress(int slot)
	{
		for (SlotContext other : contexts.values())
		{
			if (other != null && other.slot != slot && isSetupWorkflowPhase(other.phase))
			{
				return true;
			}
		}
		return false;
	}

	private GePlannedAction step(SlotContext context, GeObservedState state, Instant now)
	{
		GeObservedSlot observed = findSlot(state, context.slot);
		if (observed == null)
		{
			lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
			return GePlannedAction.none();
		}
		if (isUiProofTimedOut(context, now) && !hasCompletedAbortResult(context, observed))
		{
			lastReason = GeReasonCode.UI_STATE_TIMEOUT;
			return GePlannedAction.none();
		}
		if ((context.phase == GeTradePhase.MONITOR_BUY || context.phase == GeTradePhase.MONITOR_SELL)
			&& anotherSetupWorkflowInProgress(context.slot))
		{
			return GePlannedAction.none();
		}

		switch (context.phase)
		{
			case IDLE:
				return idle(context, state, observed, now);
			case WAIT_BUY_SETUP:
				if (state.getPromptMode() == GePromptMode.ITEM_SEARCH && state.getSetupSide() == GeTradeSide.BUY)
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_SEARCH_RESULTS;
					return action(context, GePlannedActionType.TYPE_ITEM_SEARCH);
				}
				if (state.getSetupSide() == GeTradeSide.UNKNOWN)
				{
					return pendingAction(context, GePlannedActionType.OPEN_BUY, now);
				}
				if (state.getSetupSide() != GeTradeSide.BUY)
				{
					lastReason = GeReasonCode.SETUP_SIDE_MISMATCH;
				}
				return GePlannedAction.none();
			case WAIT_SEARCH_RESULTS:
				if (state.getSetupItemId() == context.candidate.getItemId())
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_ITEM_SELECTED;
					return step(context, state, now);
				}
				if (state.hasSearchResult(context.candidate.getItemId())
					|| state.getPromptMode() == GePromptMode.NONE)
				{
					context.phase = GeTradePhase.WAIT_ITEM_SELECTED;
					return pendingAction(context, GePlannedActionType.SELECT_ITEM, now);
				}
				return GePlannedAction.none();
			case WAIT_ITEM_SELECTED:
				if (state.getSetupItemId() < 0)
				{
					if (state.hasSearchResult(context.candidate.getItemId())
						|| state.getPromptMode() == GePromptMode.NONE)
					{
						return pendingAction(context, GePlannedActionType.SELECT_ITEM, now);
					}
					return GePlannedAction.none();
				}
				if (state.getSetupItemId() != context.candidate.getItemId())
				{
					lastReason = GeReasonCode.SETUP_ITEM_MISMATCH;
					return GePlannedAction.none();
				}
				clearPending(context);
				context.phase = GeTradePhase.WAIT_QUANTITY_PROMPT;
				return pendingAction(context, GePlannedActionType.OPEN_QUANTITY, now);
			case WAIT_QUANTITY_PROMPT:
				if (state.getPromptMode() == GePromptMode.QUANTITY)
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_QUANTITY_VALUE;
					return action(context, GePlannedActionType.TYPE_QUANTITY);
				}
				if (state.getSetupItemId() == context.candidate.getItemId()
					&& state.getSetupSide() == GeTradeSide.BUY)
				{
					return pendingAction(context, GePlannedActionType.OPEN_QUANTITY, now);
				}
				return GePlannedAction.none();
			case WAIT_QUANTITY_VALUE:
				if (state.getSetupQuantity() == context.candidate.getQuantity())
				{
					context.phase = GeTradePhase.WAIT_PRICE_PROMPT;
					return pendingAction(context, GePlannedActionType.OPEN_PRICE, now);
				}
				if (state.getSetupQuantity() > 0 && state.getPromptMode() == GePromptMode.NONE)
				{
					lastReason = GeReasonCode.SETUP_QUANTITY_MISMATCH;
				}
				return GePlannedAction.none();
			case WAIT_PRICE_PROMPT:
				if (state.getPromptMode() == GePromptMode.PRICE)
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_PRICE_VALUE;
					return action(context, GePlannedActionType.TYPE_PRICE);
				}
				if (state.getSetupItemId() == context.candidate.getItemId()
					&& state.getSetupSide() == GeTradeSide.BUY)
				{
					return pendingAction(context, GePlannedActionType.OPEN_PRICE, now);
				}
				return GePlannedAction.none();
			case WAIT_PRICE_VALUE:
				if (!setupMatches(state, context.candidate.getItemId(), context.candidate.getQuantity(),
					context.candidate.getBuyPrice(), GeTradeSide.BUY))
				{
					return GePlannedAction.none();
				}
				context.phase = GeTradePhase.WAIT_BUY_SLOT;
				return pendingAction(context, GePlannedActionType.CONFIRM, now);
			case WAIT_BUY_SLOT:
				if (isPlaced(observed, context.candidate.getItemId(), context.candidate.getQuantity(),
					context.candidate.getBuyPrice(), "BUYING", "BOUGHT"))
				{
					clearPending(context);
					tradeLedger.markPlaced(context.obligationId, now);
					tradeLedger.markFilled(context.obligationId, observed.getFilledQuantity());
					context.phase = GeTradePhase.MONITOR_BUY;
				}
				else if (!observed.isEmpty())
				{
					lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
				}
				else if (state.getSetupSide() == GeTradeSide.BUY
					&& state.getSetupItemId() == context.candidate.getItemId()
					&& state.getSetupQuantity() == context.candidate.getQuantity()
					&& state.getSetupPrice() == context.candidate.getBuyPrice())
				{
					return pendingAction(context, GePlannedActionType.CONFIRM, now);
				}
				return GePlannedAction.none();
			case MONITOR_BUY:
				return monitorBuy(context, observed, now);
			case WAIT_ABORT_READY:
				if (state.isOfferDetailsVisible())
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_ABORT_RESULT;
					return pendingAction(context, GePlannedActionType.ABORT_BUY, now);
				}
				return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
			case WAIT_ABORT_RESULT:
				if ("CANCELLED_BUY".equalsIgnoreCase(observed.getState())
					|| "BOUGHT".equalsIgnoreCase(observed.getState()))
				{
					clearPending(context);
					if ("CANCELLED_BUY".equalsIgnoreCase(observed.getState()))
					{
						GeTradeObligation obligation = tradeLedger.get(context.obligationId);
						if (obligation != null && obligation.getAbortCount() == 0)
						{
							tradeLedger.incrementAbortCount(context.obligationId);
						}
					}
					context.phase = GeTradePhase.WAIT_BUY_COLLECT_READY;
					return GePlannedAction.none();
				}
				if (!state.isOfferDetailsVisible())
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_ABORT_READY;
					return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
				}
				return pendingAction(context, GePlannedActionType.ABORT_BUY, now);
			case WAIT_BUY_COLLECT_READY:
				if (!sameOwnedBuy(context, observed))
				{
					lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
					return GePlannedAction.none();
				}
				if (!state.isOfferDetailsVisible())
				{
					return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
				}
				clearPending(context);
				context.preCollectInventory = state.getInventoryQuantity(context.candidate.getItemId());
				context.preCollectGp = state.getGp();
				context.phase = GeTradePhase.WAIT_BUY_COLLECT_RESULT;
				return pendingAction(context, GePlannedActionType.COLLECT, now);
			case WAIT_BUY_COLLECT_RESULT:
				if (observed.isEmpty())
				{
					return finishBuyCollection(context, state, observed, now);
				}
				if (!sameOwnedBuy(context, observed))
				{
					lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
					return GePlannedAction.none();
				}
				if (!state.isOfferDetailsVisible())
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_BUY_COLLECT_READY;
					return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
				}
				return pendingAction(context, GePlannedActionType.COLLECT, now);
			case WAIT_SELL_SETUP:
				if (state.getSetupSide() == GeTradeSide.SELL)
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_SELL_ITEM_SELECTED;
					return pendingAction(context, GePlannedActionType.SELECT_SELL_ITEM, now);
				}
				if (state.getSetupSide() == GeTradeSide.UNKNOWN)
				{
					return pendingAction(context, GePlannedActionType.OPEN_SELL, now);
				}
				lastReason = GeReasonCode.SETUP_SIDE_MISMATCH;
				return GePlannedAction.none();
			case WAIT_SELL_ITEM_SELECTED:
				if (state.getSetupItemId() < 0)
				{
					return pendingAction(context, GePlannedActionType.SELECT_SELL_ITEM, now);
				}
				if (state.getSetupItemId() != context.candidate.getItemId())
				{
					lastReason = GeReasonCode.SETUP_ITEM_MISMATCH;
					return GePlannedAction.none();
				}
				clearPending(context);
				context.phase = GeTradePhase.WAIT_SELL_QUANTITY_PROMPT;
				return pendingAction(context, GePlannedActionType.OPEN_QUANTITY, now);
			case WAIT_SELL_QUANTITY_PROMPT:
				if (state.getPromptMode() == GePromptMode.QUANTITY)
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_SELL_QUANTITY_VALUE;
					return action(context, GePlannedActionType.TYPE_QUANTITY);
				}
				if (state.getSetupItemId() == context.candidate.getItemId()
					&& state.getSetupSide() == GeTradeSide.SELL)
				{
					return pendingAction(context, GePlannedActionType.OPEN_QUANTITY, now);
				}
				return GePlannedAction.none();
			case WAIT_SELL_QUANTITY_VALUE:
				if (state.getSetupQuantity() == context.sellQuantity)
				{
					context.phase = GeTradePhase.WAIT_SELL_PRICE_PROMPT;
					return pendingAction(context, GePlannedActionType.OPEN_PRICE, now);
				}
				if (state.getSetupQuantity() > 0 && state.getPromptMode() == GePromptMode.NONE)
				{
					lastReason = GeReasonCode.SETUP_QUANTITY_MISMATCH;
				}
				return GePlannedAction.none();
			case WAIT_SELL_PRICE_PROMPT:
				if (state.getPromptMode() == GePromptMode.PRICE)
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_SELL_PRICE_VALUE;
					return action(context, GePlannedActionType.TYPE_PRICE);
				}
				if (state.getSetupItemId() == context.candidate.getItemId()
					&& state.getSetupSide() == GeTradeSide.SELL)
				{
					return pendingAction(context, GePlannedActionType.OPEN_PRICE, now);
				}
				return GePlannedAction.none();
			case WAIT_SELL_PRICE_VALUE:
				if (!setupMatches(state, context.candidate.getItemId(), context.sellQuantity,
					context.candidate.getSellPrice(), GeTradeSide.SELL))
				{
					return GePlannedAction.none();
				}
				context.phase = GeTradePhase.WAIT_SELL_SLOT;
				return pendingAction(context, GePlannedActionType.CONFIRM, now);
			case WAIT_SELL_SLOT:
				if (isPlaced(observed, context.candidate.getItemId(), context.sellQuantity,
					context.candidate.getSellPrice(), "SELLING", "SOLD"))
				{
					clearPending(context);
					tradeLedger.markPlaced(context.obligationId, now);
					tradeLedger.markFilled(context.obligationId, observed.getFilledQuantity());
					context.phase = GeTradePhase.MONITOR_SELL;
				}
				else if (!observed.isEmpty())
				{
					lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
				}
				else if (state.getSetupSide() == GeTradeSide.SELL
					&& state.getSetupItemId() == context.candidate.getItemId()
					&& state.getSetupQuantity() == context.sellQuantity
					&& state.getSetupPrice() == context.candidate.getSellPrice())
				{
					return pendingAction(context, GePlannedActionType.CONFIRM, now);
				}
				return GePlannedAction.none();
			case MONITOR_SELL:
				if (observed.getItemId() == context.candidate.getItemId())
				{
					tradeLedger.markFilled(context.obligationId, observed.getFilledQuantity());
				}
				if ("SOLD".equalsIgnoreCase(observed.getState()))
				{
					context.phase = GeTradePhase.WAIT_SELL_COLLECT_READY;
					return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
				}
				return GePlannedAction.none();
			case WAIT_SELL_COLLECT_READY:
				if (!sameOwnedSell(context, observed))
				{
					lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
					return GePlannedAction.none();
				}
				if (!state.isOfferDetailsVisible())
				{
					return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
				}
				clearPending(context);
				context.preCollectGp = state.getGp();
				context.phase = GeTradePhase.WAIT_SELL_COLLECT_RESULT;
				return pendingAction(context, GePlannedActionType.COLLECT, now);
			case WAIT_SELL_COLLECT_RESULT:
				if (observed.isEmpty())
				{
					if (state.getGp() > context.preCollectGp)
					{
						clearPending(context);
						tradeLedger.remove(context.obligationId);
						context.reset();
					}
					return GePlannedAction.none();
				}
				if (!sameOwnedSell(context, observed))
				{
					lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
					return GePlannedAction.none();
				}
				if (!state.isOfferDetailsVisible())
				{
					clearPending(context);
					context.phase = GeTradePhase.WAIT_SELL_COLLECT_READY;
					return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
				}
				return pendingAction(context, GePlannedActionType.COLLECT, now);
			default:
				return GePlannedAction.none();
		}
	}

	private GePlannedAction idle(SlotContext context, GeObservedState state, GeObservedSlot observed, Instant now)
	{
		GeTradeObligation owned = tradeLedger.findBySlot(context.slot);
		if (!observed.isEmpty())
		{
			if (owned == null)
			{
				owned = adoptObservedOffer(context.slot, observed, now);
			}
			return resumeOwnedOffer(context, state, observed, owned, now);
		}
		if (owned != null)
		{
			if (canReconcileCollectedBuy(state, owned))
			{
				return reconcileCollectedBuy(context, state, owned, now);
			}
			if (owned.getSide() == GeTradeSide.SELL)
			{
				lastReason = GeReasonCode.COLLECT_STATE_MISMATCH;
				return GePlannedAction.none();
			}
			tradeLedger.remove(owned.getId());
		}
		if (anotherSetupWorkflowInProgress(context.slot))
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
			lastReason = GeReasonCode.NO_OPPORTUNITY;
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
			candidate.getBuyPrice(),
			candidate.getSellPrice());
		context.candidate = candidate;
		context.obligationId = obligationId;
		context.side = GeTradeSide.BUY;
		context.phase = GeTradePhase.WAIT_BUY_SETUP;
		return pendingAction(context, GePlannedActionType.OPEN_BUY, now);
	}

	private GeTradeObligation adoptObservedOffer(int slot, GeObservedSlot observed, Instant now)
	{
		if (observed == null
			|| observed.isEmpty()
			|| observed.getItemId() < 0
			|| observed.getTotalQuantity() <= 0
			|| observed.getPrice() <= 0)
		{
			return null;
		}

		String offerState = observed.getState();
		boolean buy = "BUYING".equalsIgnoreCase(offerState)
			|| "BOUGHT".equalsIgnoreCase(offerState)
			|| "CANCELLED_BUY".equalsIgnoreCase(offerState);
		boolean sell = "SELLING".equalsIgnoreCase(offerState)
			|| "SOLD".equalsIgnoreCase(offerState);
		if (!buy && !sell)
		{
			return null;
		}

		GeMarketItem marketItem = currentMarketItem(observed.getItemId());
		String itemName = marketItem == null || marketItem.getName().trim().isEmpty()
			? "Item " + observed.getItemId()
			: marketItem.getName();

		GeTradeObligation obligation;
		if (buy)
		{
			int sellPrice = marketItem == null ? 0 : marketItem.getSellPrice();
			if (sellPrice <= 0)
			{
				lastReason = GeReasonCode.MARKET_DATA_UNAVAILABLE;
				return null;
			}
			String id = "v6-orphan-buy-" + slot;
			obligation = tradeLedger.reserveBuy(
				id,
				slot,
				observed.getItemId(),
				itemName,
				observed.getTotalQuantity(),
				observed.getPrice(),
				sellPrice);
		}
		else
		{
			String id = "v6-orphan-sell-" + slot;
			obligation = tradeLedger.createSell(
				id,
				null,
				slot,
				observed.getItemId(),
				itemName,
				observed.getTotalQuantity(),
				observed.getPrice());
		}

		tradeLedger.markPlaced(obligation.getId(), now);
		tradeLedger.markFilled(obligation.getId(), observed.getFilledQuantity());
		return obligation;
	}

	private GePlannedAction resumeOwnedOffer(
		SlotContext context,
		GeObservedState state,
		GeObservedSlot observed,
		GeTradeObligation obligation,
		Instant now)
	{
		if (obligation == null)
		{
			return GePlannedAction.none();
		}

		String offerState = observed.getState();
		if (obligation.getSide() == GeTradeSide.BUY)
		{
			boolean stateMatches = "BUYING".equalsIgnoreCase(offerState)
				|| "BOUGHT".equalsIgnoreCase(offerState)
				|| "CANCELLED_BUY".equalsIgnoreCase(offerState);
			boolean identityMatches = stateMatches
				&& observed.getItemId() == obligation.getItemId()
				&& observed.getTotalQuantity() == obligation.getIntendedQuantity()
				&& observed.getPrice() == obligation.getIntendedPrice();
			if (!identityMatches)
			{
				if (canReconcileCollectedBuy(state, obligation))
				{
					return reconcileCollectedBuy(context, state, obligation, now);
				}
				lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
				return GePlannedAction.none();
			}

			int sellPrice = obligation.getTargetSellPrice();
			if (sellPrice <= 0)
			{
				sellPrice = currentSellPrice(obligation.getItemId());
			}
			if (sellPrice <= 0)
			{
				lastReason = GeReasonCode.MARKET_DATA_UNAVAILABLE;
				return GePlannedAction.none();
			}

			context.candidate = new GeCandidate(
				obligation.getItemId(),
				obligation.getItemName(),
				obligation.getIntendedPrice(),
				sellPrice,
				obligation.getIntendedQuantity(),
				0,
				0,
				0);
			context.obligationId = obligation.getId();
			context.side = GeTradeSide.BUY;
			context.phase = GeTradePhase.MONITOR_BUY;
			if (obligation.getPlacedAt() == null)
			{
				tradeLedger.markPlaced(obligation.getId(), now);
			}
			tradeLedger.markFilled(obligation.getId(), observed.getFilledQuantity());
			updateObligationSequence(obligation.getId());
			if (anotherSetupWorkflowInProgress(context.slot))
			{
				return GePlannedAction.none();
			}
			return monitorBuy(context, observed, now);
		}

		if (obligation.getSide() == GeTradeSide.SELL)
		{
			boolean stateMatches = "SELLING".equalsIgnoreCase(offerState)
				|| "SOLD".equalsIgnoreCase(offerState);
			boolean identityMatches = stateMatches
				&& observed.getItemId() == obligation.getItemId()
				&& observed.getTotalQuantity() == obligation.getIntendedQuantity()
				&& observed.getPrice() == obligation.getIntendedPrice();
			if (!identityMatches)
			{
				lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
				return GePlannedAction.none();
			}

			context.candidate = new GeCandidate(
				obligation.getItemId(),
				obligation.getItemName(),
				1,
				obligation.getIntendedPrice(),
				obligation.getIntendedQuantity(),
				0,
				0,
				0);
			context.obligationId = obligation.getId();
			context.side = GeTradeSide.SELL;
			context.sellQuantity = obligation.getIntendedQuantity();
			context.phase = GeTradePhase.MONITOR_SELL;
			if (obligation.getPlacedAt() == null)
			{
				tradeLedger.markPlaced(obligation.getId(), now);
			}
			tradeLedger.markFilled(obligation.getId(), observed.getFilledQuantity());
			updateObligationSequence(obligation.getId());
			if (anotherSetupWorkflowInProgress(context.slot))
			{
				return GePlannedAction.none();
			}
			if ("SOLD".equalsIgnoreCase(offerState))
			{
				context.phase = GeTradePhase.WAIT_SELL_COLLECT_READY;
				return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
			}
		}
		return GePlannedAction.none();
	}

	private boolean canReconcileCollectedBuy(GeObservedState state, GeTradeObligation obligation)
	{
		return state != null
			&& obligation != null
			&& obligation.getSide() == GeTradeSide.BUY
			&& obligation.getFilledQuantity() > 0
			&& state.getInventoryQuantity(obligation.getItemId()) >= obligation.getFilledQuantity();
	}

	private GePlannedAction reconcileCollectedBuy(
		SlotContext source,
		GeObservedState state,
		GeTradeObligation buy,
		Instant now)
	{
		int sellPrice = buy.getTargetSellPrice() > 0
			? buy.getTargetSellPrice() : currentSellPrice(buy.getItemId());
		if (sellPrice <= 0)
		{
			lastReason = GeReasonCode.MARKET_DATA_UNAVAILABLE;
			return GePlannedAction.none();
		}

		int quantity = Math.min(
			buy.getFilledQuantity(),
			state.getInventoryQuantity(buy.getItemId()));
		int sellSlot = findEmptyF2pSlot(state, source.slot);
		if (quantity <= 0 || sellSlot < 1)
		{
			lastReason = GeReasonCode.SLOT_IDENTITY_CHANGED;
			return GePlannedAction.none();
		}

		String parentId = buy.getId();
		tradeLedger.remove(parentId);
		limitLedger.recordFill(buy.getItemId(), quantity, now);

		String sellId = "v6-sell-" + (++obligationSequence);
		tradeLedger.createSell(
			sellId,
			parentId,
			sellSlot,
			buy.getItemId(),
			buy.getItemName(),
			quantity,
			sellPrice);

		SlotContext target = contexts.get(sellSlot);
		if (source != target)
		{
			source.reset();
		}
		target.candidate = new GeCandidate(
			buy.getItemId(), buy.getItemName(), buy.getIntendedPrice(), sellPrice,
			buy.getIntendedQuantity(), 0, 0, 0);
		target.obligationId = sellId;
		target.side = GeTradeSide.SELL;
		target.sellQuantity = quantity;
		target.phase = GeTradePhase.WAIT_SELL_SETUP;
		return pendingAction(target, GePlannedActionType.OPEN_SELL, now);
	}

	private static int findEmptyF2pSlot(GeObservedState state, int preferred)
	{
		GeObservedSlot preferredSlot = findSlot(state, preferred);
		if (preferredSlot != null && preferredSlot.isEmpty())
		{
			return preferred;
		}
		for (int slot = 1; slot <= 3; slot++)
		{
			GeObservedSlot observed = findSlot(state, slot);
			if (observed != null && observed.isEmpty())
			{
				return slot;
			}
		}
		return -1;
	}

	private GeMarketItem currentMarketItem(int itemId)
	{
		GeMarketSnapshot market = marketSupplier == null ? null : marketSupplier.get();
		if (market == null)
		{
			return null;
		}
		for (GeMarketItem item : market.getItems())
		{
			if (item != null && item.getItemId() == itemId)
			{
				return item;
			}
		}
		return null;
	}

	private int currentSellPrice(int itemId)
	{
		GeMarketItem item = currentMarketItem(itemId);
		return item == null ? 0 : item.getSellPrice();
	}

	private void updateObligationSequence(String id)
	{
		if (id == null)
		{
			return;
		}
		int dash = id.lastIndexOf('-');
		if (dash < 0 || dash + 1 >= id.length())
		{
			return;
		}
		try
		{
			obligationSequence = Math.max(obligationSequence, Long.parseLong(id.substring(dash + 1)));
		}
		catch (NumberFormatException ignored)
		{
			// Non-sequential legacy IDs are safe to ignore.
		}
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
			return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
		}

		GeTradeObligation obligation = tradeLedger.get(context.obligationId);
		if (obligation != null && obligation.getPlacedAt() != null
			&& obligation.getAbortCount() == 0
			&& !now.isBefore(obligation.getPlacedAt().plus(BUY_TIMEOUT)))
		{
			context.phase = GeTradePhase.WAIT_ABORT_READY;
			return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
		}
		return GePlannedAction.none();
	}

	private GePlannedAction finishBuyCollection(
		SlotContext context,
		GeObservedState state,
		GeObservedSlot observed,
		Instant now)
	{
		int after = state.getInventoryQuantity(context.candidate.getItemId());
		int actualReceived = Math.max(0, after - context.preCollectInventory);
		if (!observed.isEmpty())
		{
			return GePlannedAction.none();
		}

		GeTradeObligation obligation = tradeLedger.get(context.obligationId);
		int expectedFilled = obligation == null ? 0 : obligation.getFilledQuantity();
		if (actualReceived <= 0)
		{
			if (expectedFilled == 0 && state.getGp() > context.preCollectGp)
			{
				clearPending(context);
				tradeLedger.remove(context.obligationId);
				context.reset();
			}
			return GePlannedAction.none();
		}

		clearPending(context);
		String parentId = context.obligationId;
		limitLedger.recordFill(context.candidate.getItemId(), actualReceived, now);
		tradeLedger.remove(parentId);

		String sellId = "v6-sell-" + (++obligationSequence);
		tradeLedger.createSell(
			sellId,
			parentId,
			context.slot,
			context.candidate.getItemId(),
			context.candidate.getName(),
			actualReceived,
			context.candidate.getSellPrice());
		context.obligationId = sellId;
		context.side = GeTradeSide.SELL;
		context.sellQuantity = actualReceived;
		context.phase = GeTradePhase.WAIT_SELL_SETUP;
		return pendingAction(context, GePlannedActionType.OPEN_SELL, now);
	}

	private boolean sameOwnedBuy(SlotContext context, GeObservedSlot observed)
	{
		if (context.candidate == null || observed == null)
		{
			return false;
		}
		String state = observed.getState();
		return ("BUYING".equalsIgnoreCase(state)
			|| "BOUGHT".equalsIgnoreCase(state)
			|| "CANCELLED_BUY".equalsIgnoreCase(state))
			&& observed.getItemId() == context.candidate.getItemId()
			&& observed.getTotalQuantity() == context.candidate.getQuantity()
			&& observed.getPrice() == context.candidate.getBuyPrice();
	}

	private boolean sameOwnedSell(SlotContext context, GeObservedSlot observed)
	{
		if (context.candidate == null || observed == null)
		{
			return false;
		}
		String state = observed.getState();
		return ("SELLING".equalsIgnoreCase(state) || "SOLD".equalsIgnoreCase(state))
			&& observed.getItemId() == context.candidate.getItemId()
			&& observed.getTotalQuantity() == context.sellQuantity
			&& observed.getPrice() == context.candidate.getSellPrice();
	}

	private GePlannedAction pendingAction(
		SlotContext context,
		GePlannedActionType type,
		Instant now)
	{
		if (context.pending == null || !context.pending.matches(type))
		{
			context.pending = new GePendingUiOperation(type, now);
		}
		context.pending.markAttempt(now);
		return action(context, type);
	}

	private static void clearPending(SlotContext context)
	{
		context.pending = null;
	}

	private GePlannedAction action(SlotContext context, GePlannedActionType type)
	{
		GeCandidate candidate = context.candidate;
		int quantity = context.side == GeTradeSide.SELL ? context.sellQuantity
			: candidate == null ? 0 : candidate.getQuantity();
		int price = context.side == GeTradeSide.SELL
			? candidate == null ? 0 : candidate.getSellPrice()
			: candidate == null ? 0 : candidate.getBuyPrice();
		return GePlannedAction.of(
			type,
			context.slot,
			candidate == null ? -1 : candidate.getItemId(),
			candidate == null ? "" : candidate.getName(),
			quantity,
			price,
			context.obligationId);
	}

	private boolean setupMatches(
		GeObservedState state,
		int itemId,
		int quantity,
		int price,
		GeTradeSide side)
	{
		if (state.getSetupItemId() != itemId)
		{
			lastReason = GeReasonCode.SETUP_ITEM_MISMATCH;
			return false;
		}
		if (state.getSetupQuantity() != quantity)
		{
			lastReason = GeReasonCode.SETUP_QUANTITY_MISMATCH;
			return false;
		}
		if (state.getSetupPrice() != price)
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

	private static boolean isPlaced(
		GeObservedSlot observed,
		int itemId,
		int quantity,
		int price,
		String stateA,
		String stateB)
	{
		String state = observed.getState();
		return (stateA.equalsIgnoreCase(state) || stateB.equalsIgnoreCase(state))
			&& observed.getItemId() == itemId
			&& observed.getTotalQuantity() == quantity
			&& observed.getPrice() == price;
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
		private GeTradeSide side = GeTradeSide.UNKNOWN;
		private int sellQuantity;
		private int preCollectInventory;
		private long preCollectGp;
		private Instant phaseEnteredAt;
		private GePendingUiOperation pending;

		private SlotContext(int slot)
		{
			this.slot = slot;
		}

		private void reset()
		{
			phase = GeTradePhase.IDLE;
			candidate = null;
			obligationId = "";
			side = GeTradeSide.UNKNOWN;
			sellQuantity = 0;
			preCollectInventory = 0;
			preCollectGp = 0L;
			phaseEnteredAt = null;
			pending = null;
		}
	}
}
