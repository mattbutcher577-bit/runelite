package net.runelite.client.plugins.geautotrader;

import com.google.gson.Gson;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GeMarketServiceDiagnosticsTest
{
	@Test
	public void testRefreshFailureIsRetainedForRuntimeDiagnostics() throws Exception
	{
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try
		{
			GeMarketService service = new GeMarketService(
				url -> { throw new IOException("HTTP 403 test block"); },
				new Gson(), Duration.ZERO, executor);

			service.refreshAsync();
			long deadline = System.currentTimeMillis() + 2000L;
			while (service.isRefreshing() && System.currentTimeMillis() < deadline)
			{
				Thread.sleep(10L);
			}

			assertNull(service.snapshot());
			assertTrue(service.getLastError().contains("HTTP 403 test block"));
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
