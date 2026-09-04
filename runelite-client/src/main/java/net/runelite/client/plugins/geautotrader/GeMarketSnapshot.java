package net.runelite.client.plugins.geautotrader;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GeMarketSnapshot
{
	private final Instant fetchedAt;
	private final List<GeMarketItem> items;

	public GeMarketSnapshot(Instant fetchedAt, List<GeMarketItem> items)
	{
		this.fetchedAt = fetchedAt;
		this.items = Collections.unmodifiableList(new ArrayList<>(
			items == null ? Collections.emptyList() : items));
	}

	public Instant getFetchedAt()
	{
		return fetchedAt;
	}

	public List<GeMarketItem> getItems()
	{
		return items;
	}

	public boolean isStale(Instant now, Duration maxAge)
	{
		return fetchedAt == null || now.isAfter(fetchedAt.plus(maxAge));
	}
}
