package net.runelite.client.plugins.geautotrader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;

public final class GeWidgetActionResolver
{
	private static final int MAX_WIDGETS = 512;

	private GeWidgetActionResolver()
	{
	}

	public static GeWidgetActionSpec findUnique(Widget root, String... aliases)
	{
		List<GeWidgetActionSpec> all = findAll(root, aliases);
		return all.size() == 1 ? all.get(0) : null;
	}

	public static List<GeWidgetActionSpec> findAll(Widget root, String... aliases)
	{
		if (root == null)
		{
			return Collections.emptyList();
		}

		Set<String> accepted = new HashSet<>();
		for (String alias : aliases)
		{
			String normalized = normalize(alias);
			if (!normalized.isEmpty())
			{
				accepted.add(normalized);
			}
		}
		if (accepted.isEmpty())
		{
			return Collections.emptyList();
		}

		List<GeWidgetActionSpec> result = new ArrayList<>();
		Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<Widget> queue = new ArrayDeque<>();
		queue.add(root);

		while (!queue.isEmpty() && seen.size() < MAX_WIDGETS)
		{
			Widget widget = queue.removeFirst();
			if (widget == null || !seen.add(widget) || widget.isHidden())
			{
				continue;
			}

			String[] actions = widget.getActions();
			if (actions != null)
			{
				for (int i = 0; i < actions.length; i++)
				{
					if (accepted.contains(normalize(actions[i])))
					{
						MenuAction type = i < 5 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY;
						result.add(new GeWidgetActionSpec(
							widget.getIndex(),
							widget.getId(),
							type,
							i + 1,
							widget.getItemId(),
							actions[i],
							widget.getName()));
						break;
					}
				}
			}

			enqueue(queue, widget.getChildren());
			enqueue(queue, widget.getDynamicChildren());
			enqueue(queue, widget.getStaticChildren());
			enqueue(queue, widget.getNestedChildren());
		}
		return result;
	}

	private static void enqueue(Deque<Widget> queue, Widget[] children)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			if (child != null)
			{
				queue.addLast(child);
			}
		}
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
	}
}
