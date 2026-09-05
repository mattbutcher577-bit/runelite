package net.runelite.client.plugins.gebridge;

import java.awt.Rectangle;
import lombok.Value;

@Value
class GeBridgeBounds
{
	int x;
	int y;
	int width;
	int height;
	boolean valid;

	static GeBridgeBounds from(Rectangle rectangle)
	{
		if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0)
		{
			return invalid();
		}

		return new GeBridgeBounds(rectangle.x, rectangle.y, rectangle.width, rectangle.height, true);
	}

	static GeBridgeBounds union(GeBridgeBounds... bounds)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		boolean any = false;
		if (bounds != null)
		{
			for (GeBridgeBounds bound : bounds)
			{
				if (bound == null || !bound.isValid())
				{
					continue;
				}
				any = true;
				minX = Math.min(minX, bound.getX());
				minY = Math.min(minY, bound.getY());
				maxX = Math.max(maxX, bound.getX() + bound.getWidth());
				maxY = Math.max(maxY, bound.getY() + bound.getHeight());
			}
		}
		return any ? new GeBridgeBounds(minX, minY, maxX - minX, maxY - minY, true) : invalid();
	}

	static GeBridgeBounds invalid()
	{
		return new GeBridgeBounds(-1, -1, 0, 0, false);
	}
}
