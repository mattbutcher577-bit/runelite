package net.runelite.client.plugins.geautotrader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class GeTradeLedgerTest
{
	@Test
	public void testOutstandingBuyGpIsReservedAcrossSlots()
	{
		GeTradeLedger ledger = new GeTradeLedger();
		ledger.reserveBuy("a", 1, 1127, "Adamant platebody", 100, 9_000);
		ledger.reserveBuy("b", 2, 1319, "Rune 2h sword", 10, 37_000);
		assertEquals(1_270_000L, ledger.reservedGp());
	}

	@Test
	public void testFilledQuantityReleasesReservation()
	{
		GeTradeLedger ledger = new GeTradeLedger();
		ledger.reserveBuy("a", 1, 1127, "Adamant platebody", 100, 9_000);
		ledger.reserveBuy("b", 2, 1319, "Rune 2h sword", 10, 37_000);
		ledger.markFilled("a", 100);
		assertEquals(370_000L, ledger.reservedGp());
	}

	@Test
	public void testCompletedObligationCanBeRemoved()
	{
		GeTradeLedger ledger = new GeTradeLedger();
		ledger.reserveBuy("a", 1, 1127, "Adamant platebody", 10, 9_000);
		ledger.remove("a");
		assertNull(ledger.get("a"));
		assertEquals(0L, ledger.reservedGp());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSlotFourCannotBeReserved()
	{
		new GeTradeLedger().reserveBuy("x", 4, 1127, "Adamant platebody", 1, 9_000);
	}
}
