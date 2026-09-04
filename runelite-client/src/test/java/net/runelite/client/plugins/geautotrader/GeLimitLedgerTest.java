package net.runelite.client.plugins.geautotrader;

import java.time.Duration;
import java.time.Instant;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeLimitLedgerTest
{
	@Test
	public void testPartialFillConsumesOnlyActualQuantity()
	{
		GeLimitLedger ledger = new GeLimitLedger();
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		ledger.recordFill(1127, 40, now);
		assertEquals(85, ledger.remaining(1127, 125, now.plusSeconds(60)));
	}

	@Test
	public void testFourHourFillExpires()
	{
		GeLimitLedger ledger = new GeLimitLedger();
		Instant t0 = Instant.parse("2026-09-04T10:00:00Z");
		ledger.recordFill(1127, 125, t0);
		assertEquals(125,
			ledger.remaining(1127, 125, t0.plus(Duration.ofHours(4)).plusSeconds(1)));
	}

	@Test
	public void testLimitNeverGoesNegative()
	{
		GeLimitLedger ledger = new GeLimitLedger();
		Instant now = Instant.parse("2026-09-04T18:00:00Z");
		ledger.recordFill(1127, 200, now);
		assertEquals(0, ledger.remaining(1127, 125, now));
	}
}
