package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class GeBridgeWidgetActionResolver
{
	private GeBridgeWidgetActionResolver()
	{
	}

	static GeBridgeBounds findUnique(Widget root, String... aliases)
	{
		List<GeBridgeBounds> matches = findAll(root, aliases);
		return matches.size() == 1 ? matches.get(0) : GeBridgeBounds.invalid();
	}

	static List<GeBridgeBounds> findAll(Widget root, String... aliases)
	{
		Set<String> accepted = new HashSet<>();
		for (String alias : aliases)
		{
			String normalized = normalize(alias);
			if (!normalized.isEmpty())
			{
				accepted.add(normalized);
			}
		}

		List<GeBridgeBounds> results = new ArrayList<>();
		collect(root, accepted, results, new HashSet<>());
		results.sort(Comparator
			.comparingInt(GeBridgeBounds::getY)
			.thenComparingInt(GeBridgeBounds::getX)
			.thenComparingInt(GeBridgeBounds::getWidth)
			.thenComparingInt(GeBridgeBounds::getHeight));
		return results;
	}

	private static void collect(
		Widget widget,
		Set<String> accepted,
		List<GeBridgeBounds> results,
		Set<Integer> seenWidgetIds)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}

		int id = widget.getId();
		if (id != -1 && !seenWidgetIds.add(id))
		{
			return;
		}

		if (matches(widget, accepted))
		{
			GeBridgeBounds bounds = GeBridgeBounds.from(widget.getBounds());
			if (bounds.isValid() && !containsBounds(results, bounds))
			{
				results.add(bounds);
			}
		}

		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				collect(child, accepted, results, seenWidgetIds);
			}
		}
	}

	private static boolean matches(Widget widget, Set<String> accepted)
	{
		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (accepted.contains(normalize(action)))
				{
					return true;
				}
			}
		}
		return accepted.contains(normalize(widget.getText()))
			|| accepted.contains(normalize(widget.getName()));
	}

	private static String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}
		String clean = Text.removeTags(value);
		return clean == null ? "" : clean.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
	}

	private static boolean containsBounds(List<GeBridgeBounds> results, GeBridgeBounds candidate)
	{
		for (GeBridgeBounds existing : results)
		{
			if (existing.getX() == candidate.getX()
				&& existing.getY() == candidate.getY()
				&& existing.getWidth() == candidate.getWidth()
				&& existing.getHeight() == candidate.getHeight())
			{
				return true;
			}
		}
		return false;
	}
}
