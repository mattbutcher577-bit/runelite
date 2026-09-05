package net.runelite.client.plugins.geautotrader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeObservedState
{
	private final boolean loggedIn;
	private final boolean membersWorld;
	private final boolean loginSettled;
	private final boolean geOpen;
	private final boolean blockerActive;
	private final int world;
	private final long gp;
	private final List<GeObservedSlot> slots;
	private final Map<Integer, Integer> inventoryCounts;
	private final int setupItemId;
	private final int setupQuantity;
	private final int setupPrice;
	private final GeTradeSide setupSide;
	private final GePromptMode promptMode;
	private final Set<Integer> searchResultItemIds;
	private final boolean offerDetailsVisible;

	public GeObservedState(
		boolean loggedIn,
		boolean membersWorld,
		boolean loginSettled,
		boolean geOpen,
		boolean blockerActive,
		long gp,
		List<GeObservedSlot> slots,
		int setupItemId,
		int setupQuantity,
		int setupPrice,
		GeTradeSide setupSide)
	{
		this(
			loggedIn,
			membersWorld,
			loginSettled,
			geOpen,
			blockerActive,
			-1,
			gp,
			slots,
			Collections.emptyMap(),
			setupItemId,
			setupQuantity,
			setupPrice,
			setupSide,
			GePromptMode.UNKNOWN,
			Collections.emptySet(),
			true);
	}

	public GeObservedState(
		boolean loggedIn,
		boolean membersWorld,
		boolean loginSettled,
		boolean geOpen,
		boolean blockerActive,
		int world,
		long gp,
		List<GeObservedSlot> slots,
		Map<Integer, Integer> inventoryCounts,
		int setupItemId,
		int setupQuantity,
		int setupPrice,
		GeTradeSide setupSide,
		GePromptMode promptMode)
	{
		this(loggedIn, membersWorld, loginSettled, geOpen, blockerActive, world, gp, slots,
			inventoryCounts, setupItemId, setupQuantity, setupPrice, setupSide, promptMode,
			Collections.emptySet(), true);
	}

	public GeObservedState(
		boolean loggedIn,
		boolean membersWorld,
		boolean loginSettled,
		boolean geOpen,
		boolean blockerActive,
		int world,
		long gp,
		List<GeObservedSlot> slots,
		Map<Integer, Integer> inventoryCounts,
		int setupItemId,
		int setupQuantity,
		int setupPrice,
		GeTradeSide setupSide,
		GePromptMode promptMode,
		Set<Integer> searchResultItemIds)
	{
		this(loggedIn, membersWorld, loginSettled, geOpen, blockerActive, world, gp, slots,
			inventoryCounts, setupItemId, setupQuantity, setupPrice, setupSide, promptMode,
			searchResultItemIds, true);
	}

	public GeObservedState(
		boolean loggedIn,
		boolean membersWorld,
		boolean loginSettled,
		boolean geOpen,
		boolean blockerActive,
		int world,
		long gp,
		List<GeObservedSlot> slots,
		Map<Integer, Integer> inventoryCounts,
		int setupItemId,
		int setupQuantity,
		int setupPrice,
		GeTradeSide setupSide,
		GePromptMode promptMode,
		Set<Integer> searchResultItemIds,
		boolean offerDetailsVisible)
	{
		this.loggedIn = loggedIn;
		this.membersWorld = membersWorld;
		this.loginSettled = loginSettled;
		this.geOpen = geOpen;
		this.blockerActive = blockerActive;
		this.world = world;
		this.gp = gp;
		this.slots = Collections.unmodifiableList(new ArrayList<>(slots == null ? Collections.emptyList() : slots));
		this.inventoryCounts = Collections.unmodifiableMap(new HashMap<>(
			inventoryCounts == null ? Collections.emptyMap() : inventoryCounts));
		this.setupItemId = setupItemId;
		this.setupQuantity = setupQuantity;
		this.setupPrice = setupPrice;
		this.setupSide = setupSide == null ? GeTradeSide.UNKNOWN : setupSide;
		this.promptMode = promptMode == null ? GePromptMode.UNKNOWN : promptMode;
		this.searchResultItemIds = Collections.unmodifiableSet(new HashSet<>(
			searchResultItemIds == null ? Collections.emptySet() : searchResultItemIds));
		this.offerDetailsVisible = offerDetailsVisible;
	}

	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	public boolean isMembersWorld()
	{
		return membersWorld;
	}

	public boolean isLoginSettled()
	{
		return loginSettled;
	}

	public boolean isGeOpen()
	{
		return geOpen;
	}

	public boolean isBlockerActive()
	{
		return blockerActive;
	}

	public int getWorld()
	{
		return world;
	}

	public long getGp()
	{
		return gp;
	}

	public List<GeObservedSlot> getSlots()
	{
		return slots;
	}

	public Map<Integer, Integer> getInventoryCounts()
	{
		return inventoryCounts;
	}

	public int getInventoryQuantity(int itemId)
	{
		Integer value = inventoryCounts.get(itemId);
		return value == null ? 0 : value;
	}

	public int getSetupItemId()
	{
		return setupItemId;
	}

	public int getSetupQuantity()
	{
		return setupQuantity;
	}

	public int getSetupPrice()
	{
		return setupPrice;
	}

	public GeTradeSide getSetupSide()
	{
		return setupSide;
	}

	public GePromptMode getPromptMode()
	{
		return promptMode;
	}

	public Set<Integer> getSearchResultItemIds()
	{
		return searchResultItemIds;
	}

	public boolean hasSearchResult(int itemId)
	{
		return searchResultItemIds.contains(itemId);
	}

	public boolean isOfferDetailsVisible()
	{
		return offerDetailsVisible;
	}
}
