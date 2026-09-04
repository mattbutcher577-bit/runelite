package net.runelite.client.plugins.geautotrader;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class GeLimitLedger
{
	private static final Duration WINDOW = Duration.ofHours(4);
	private final Map<Integer, Deque<Fill>> fills = new HashMap<>();

	public void recordFill(int itemId, int quantity, Instant at)
	{
		if (itemId <= 0 || quantity <= 0 || at == null)
		{
			throw new IllegalArgumentException("valid itemId, quantity and time required");
		}
		fills.computeIfAbsent(itemId, ignored -> new ArrayDeque<>()).addLast(new Fill(quantity, at));
	}

	public int remaining(int itemId, int configuredLimit, Instant now)
	{
		if (configuredLimit <= 0 || now == null)
		{
			return 0;
		}
		Deque<Fill> queue = fills.get(itemId);
		if (queue == null)
		{
			return configuredLimit;
		}
		Instant cutoff = now.minus(WINDOW);
		while (!queue.isEmpty() && queue.peekFirst().at.isBefore(cutoff))
		{
			queue.removeFirst();
		}
		long used = 0L;
		for (Fill fill : queue)
		{
			used += fill.quantity;
		}
		long remaining = Math.max(0L, (long) configuredLimit - used);
		return (int) Math.min(Integer.MAX_VALUE, remaining);
	}

	private static final class Fill
	{
		private final int quantity;
		private final Instant at;

		private Fill(int quantity, Instant at)
		{
			this.quantity = quantity;
			this.at = at;
		}
	}
}
