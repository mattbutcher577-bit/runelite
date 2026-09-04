package net.runelite.client.plugins.geautotrader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GeObservedState
{
	private final boolean loggedIn;
	private final boolean membersWorld;
	private final boolean loginSettled;
	private final boolean geOpen;
	private final boolean blockerActive;
	private final long gp;
	private final List<GeObservedSlot> slots;
	private final int setupItemId;
	private final int setupQuantity;
	private final int setupPrice;
	private final GeTradeSide setupSide;

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
		this.loggedIn = loggedIn;
		this.membersWorld = membersWorld;
		this.loginSettled = loginSettled;
		this.geOpen = geOpen;
		this.blockerActive = blockerActive;
		this.gp = gp;
		this.slots = Collections.unmodifiableList(new ArrayList<>(slots == null ? Collections.emptyList() : slots));
		this.setupItemId = setupItemId;
		this.setupQuantity = setupQuantity;
		this.setupPrice = setupPrice;
		this.setupSide = setupSide == null ? GeTradeSide.UNKNOWN : setupSide;
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

	public long getGp()
	{
		return gp;
	}

	public List<GeObservedSlot> getSlots()
	{
		return slots;
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
}
