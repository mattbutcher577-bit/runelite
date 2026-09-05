package net.runelite.client.plugins.geautotrader;

import net.runelite.api.MenuAction;

public final class GeWidgetActionSpec
{
	private final int param0;
	private final int param1;
	private final MenuAction action;
	private final int identifier;
	private final int itemId;
	private final String option;
	private final String target;

	public GeWidgetActionSpec(
		int param0,
		int param1,
		MenuAction action,
		int identifier,
		int itemId,
		String option,
		String target)
	{
		this.param0 = param0;
		this.param1 = param1;
		this.action = action;
		this.identifier = identifier;
		this.itemId = itemId;
		this.option = option == null ? "" : option;
		this.target = target == null ? "" : target;
	}

	public int getParam0()
	{
		return param0;
	}

	public int getParam1()
	{
		return param1;
	}

	public MenuAction getAction()
	{
		return action;
	}

	public int getIdentifier()
	{
		return identifier;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getOption()
	{
		return option;
	}

	public String getTarget()
	{
		return target;
	}
}
