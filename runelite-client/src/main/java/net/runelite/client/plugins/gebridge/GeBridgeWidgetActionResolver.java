package net.runelite.client.plugins.gebridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
		Set<Widget> seenWidgets = Collections.newSetFromMap(new IdentityHashMap<>());
		collect(root, accepted, results, seenWidgets);
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
		Set<Widget> seenWidgets)
	{
		if (widget == null || widget.isHidden() || !seenWidgets.add(widget))
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

		collectChildren(widget.getChildren(), accepted, results, seenWidgets);
		collectChildren(widget.getDynamicChildren(), accepted, results, seenWidgets);
		collectChildren(widget.getStaticChildren(), accepted, results, seenWidgets);
		collectChildren(widget.getNestedChildren(), accepted, results, seenWidgets);
	}

	private static void collectChildren(
		Widget[] children,
		Set<String> accepted,
		List<GeBridgeBounds> results,
		Set<Widget> seenWidgets)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			collect(child, accepted, results, seenWidgets);
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
