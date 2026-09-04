package net.runelite.client.plugins.geautotrader;

public final class GeSafetyPolicy
{
	private GeSafetyPolicy()
	{
	}

	public static GeReasonCode evaluateGlobal(GeObservedState state, boolean enabled, boolean stopped)
	{
		if (stopped)
		{
			return GeReasonCode.STOPPED_F8;
		}
		if (!enabled)
		{
			return GeReasonCode.DISABLED;
		}
		if (state == null || !state.isLoggedIn())
		{
			return GeReasonCode.GAME_NOT_LOGGED_IN;
		}
		if (state.isMembersWorld())
		{
			return GeReasonCode.WORLD_NOT_F2P;
		}
		if (!state.isLoginSettled())
		{
			return GeReasonCode.LOGIN_RESYNC;
		}
		if (!state.isGeOpen())
		{
			return GeReasonCode.GE_NOT_OPEN;
		}
		if (state.isBlockerActive())
		{
			return GeReasonCode.BLOCKER_ACTIVE;
		}
		return GeReasonCode.OK;
	}

	public static boolean canUseSlot(int slot)
	{
		return slot >= 1 && slot <= 3;
	}
}
