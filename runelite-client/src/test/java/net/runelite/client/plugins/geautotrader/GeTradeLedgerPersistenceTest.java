package net.runelite.client.plugins.geautotrader;

import com.google.gson.Gson;
import java.time.Instant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class GeTradeLedgerPersistenceTest
{
	@Test
	public void testRoundTripPreservesOwnedBuyRecoveryData()
	{
		Gson gson = new Gson();
		GeTradeLedger source = new GeTradeLedger();
		source.reserveBuy("v6-buy-12", 2, 845, "Oak longbow", 1000, 14, 18);
		source.markPlaced("v6-buy-12", Instant.parse("2026-09-05T09:30:00Z"));
		source.markFilled("v6-buy-12", 250);

		String encoded = GeTradeLedgerPersistence.encode(gson, source);
		GeTradeLedger restored = new GeTradeLedger();
		GeTradeLedgerPersistence.restore(gson, encoded, restored);

		GeTradeObligation obligation = restored.get("v6-buy-12");
		assertNotNull(obligation);
		assertEquals(2, obligation.getSlot());
		assertEquals(GeTradeSide.BUY, obligation.getSide());
		assertEquals(845, obligation.getItemId());
		assertEquals("Oak longbow", obligation.getItemName());
		assertEquals(1000, obligation.getIntendedQuantity());
		assertEquals(14, obligation.getIntendedPrice());
		assertEquals(18, obligation.getTargetSellPrice());
		assertEquals(250, obligation.getFilledQuantity());
		assertEquals(Instant.parse("2026-09-05T09:30:00Z"), obligation.getPlacedAt());
	}
}
