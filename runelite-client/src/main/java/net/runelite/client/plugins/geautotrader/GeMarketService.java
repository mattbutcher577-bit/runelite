package net.runelite.client.plugins.geautotrader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class GeMarketService implements AutoCloseable
{
	static final String MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
	static final String LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
	static final String FIVE_MINUTE_URL = "https://prices.runescape.wiki/api/v1/osrs/5m";
	private static final String USER_AGENT = "RuneLite-GE-AutoTrader-V6/1.0 (private fork)";

	interface Fetcher
	{
		String get(String url) throws IOException;
	}

	private final Fetcher fetcher;
	private final Gson gson;
	private final Duration minRefreshInterval;
	private final ExecutorService executor;
	private final AtomicReference<GeMarketSnapshot> snapshot = new AtomicReference<>();
	private final AtomicBoolean refreshing = new AtomicBoolean();

	public GeMarketService(OkHttpClient httpClient, Gson gson, Duration minRefreshInterval)
	{
		this(new OkHttpFetcher(httpClient), gson, minRefreshInterval,
			Executors.newSingleThreadExecutor(runnable ->
			{
				Thread thread = new Thread(runnable, "ge-v6-market");
				thread.setDaemon(true);
				return thread;
			}));
	}

	GeMarketService(Fetcher fetcher, Gson gson, Duration minRefreshInterval, ExecutorService executor)
	{
		this.fetcher = fetcher;
		this.gson = gson;
		this.minRefreshInterval = minRefreshInterval == null ? Duration.ofSeconds(30) : minRefreshInterval;
		this.executor = executor;
	}

	public GeMarketSnapshot snapshot()
	{
		return snapshot.get();
	}

	public boolean isRefreshing()
	{
		return refreshing.get();
	}

	public void refreshAsync()
	{
		GeMarketSnapshot current = snapshot.get();
		Instant now = Instant.now();
		if (current != null && current.getFetchedAt() != null
			&& now.isBefore(current.getFetchedAt().plus(minRefreshInterval)))
		{
			return;
		}
		if (!refreshing.compareAndSet(false, true))
		{
			return;
		}
		executor.execute(() ->
		{
			try
			{
				snapshot.set(fetchSnapshot(Instant.now()));
			}
			catch (IOException | RuntimeException ignored)
			{
				// Keep the last known-good immutable snapshot. Safety policy decides if it is too stale.
			}
			finally
			{
				refreshing.set(false);
			}
		});
	}

	GeMarketSnapshot fetchSnapshot(Instant fetchedAt) throws IOException
	{
		JsonArray mapping = gson.fromJson(fetcher.get(MAPPING_URL), JsonArray.class);
		JsonObject latestRoot = gson.fromJson(fetcher.get(LATEST_URL), JsonObject.class);
		JsonObject fiveRoot = gson.fromJson(fetcher.get(FIVE_MINUTE_URL), JsonObject.class);
		JsonObject latest = object(latestRoot, "data");
		JsonObject five = object(fiveRoot, "data");

		Map<Integer, MappingRow> rows = new HashMap<>();
		if (mapping != null)
		{
			for (JsonElement element : mapping)
			{
				if (element == null || !element.isJsonObject())
				{
					continue;
				}
				JsonObject row = element.getAsJsonObject();
				int id = integer(row, "id", -1);
				String name = string(row, "name");
				boolean members = bool(row, "members", true);
				int limit = integer(row, "limit", 0);
				if (id > 0 && !name.isEmpty() && limit > 0)
				{
					rows.put(id, new MappingRow(id, name, members, limit));
				}
			}
		}

		List<GeMarketItem> items = new ArrayList<>();
		for (MappingRow row : rows.values())
		{
			JsonObject current = child(latest, Integer.toString(row.id));
			JsonObject recent = child(five, Integer.toString(row.id));
			int low = integer(current, "low", 0);
			int high = integer(current, "high", 0);
			int volume = integer(recent, "highPriceVolume", 0) + integer(recent, "lowPriceVolume", 0);
			if (low <= 0 || high <= 0)
			{
				continue;
			}
			items.add(new GeMarketItem(
				row.id, row.name, row.members, row.limit, low, high, volume));
		}
		return new GeMarketSnapshot(fetchedAt, items);
	}

	@Override
	public void close()
	{
		executor.shutdownNow();
	}

	private static JsonObject object(JsonObject root, String key)
	{
		return root == null ? new JsonObject() : child(root, key);
	}

	private static JsonObject child(JsonObject root, String key)
	{
		if (root == null || !root.has(key) || root.get(key) == null || !root.get(key).isJsonObject())
		{
			return new JsonObject();
		}
		return root.getAsJsonObject(key);
	}

	private static int integer(JsonObject object, String key, int fallback)
	{
		try
		{
			return object != null && object.has(key) && !object.get(key).isJsonNull()
				? object.get(key).getAsInt() : fallback;
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static String string(JsonObject object, String key)
	{
		try
		{
			return object != null && object.has(key) && !object.get(key).isJsonNull()
				? object.get(key).getAsString().trim() : "";
		}
		catch (RuntimeException ex)
		{
			return "";
		}
	}

	private static boolean bool(JsonObject object, String key, boolean fallback)
	{
		try
		{
			return object != null && object.has(key) && !object.get(key).isJsonNull()
				? object.get(key).getAsBoolean() : fallback;
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static final class MappingRow
	{
		private final int id;
		private final String name;
		private final boolean members;
		private final int limit;

		private MappingRow(int id, String name, boolean members, int limit)
		{
			this.id = id;
			this.name = name;
			this.members = members;
			this.limit = limit;
		}
	}

	private static final class OkHttpFetcher implements Fetcher
	{
		private final OkHttpClient client;

		private OkHttpFetcher(OkHttpClient client)
		{
			this.client = client;
		}

		@Override
		public String get(String url) throws IOException
		{
			Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", USER_AGENT)
				.get()
				.build();
			try (Response response = client.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					throw new IOException("market HTTP " + response.code());
				}
				ResponseBody body = response.body();
				if (body == null)
				{
					throw new IOException("market response body missing");
				}
				return body.string();
			}
		}
	}
}
