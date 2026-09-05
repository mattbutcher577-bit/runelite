package net.runelite.client.plugins.geautotrader;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class GeTradeLedgerPersistence
{
	private static final int MAX_RECORDS = 3;
	private static final int MAX_ABORT_COUNT = 4;

	private GeTradeLedgerPersistence()
	{
	}

	static String encode(Gson gson, GeTradeLedger ledger)
	{
		if (gson == null || ledger == null)
		{
			return "[]";
		}

		List<Record> records = new ArrayList<>();
		for (GeTradeObligation obligation : ledger.all())
		{
			if (obligation == null || records.size() >= MAX_RECORDS)
			{
				continue;
			}
			Record record = new Record();
			record.id = obligation.getId();
			record.slot = obligation.getSlot();
			record.side = obligation.getSide() == null ? "" : obligation.getSide().name();
			record.itemId = obligation.getItemId();
			record.itemName = obligation.getItemName();
			record.quantity = obligation.getIntendedQuantity();
			record.price = obligation.getIntendedPrice();
			record.parentId = obligation.getParentId();
			record.targetSellPrice = obligation.getTargetSellPrice();
			record.placedAtEpochMs = obligation.getPlacedAt() == null
				? -1L : obligation.getPlacedAt().toEpochMilli();
			record.filledQuantity = obligation.getFilledQuantity();
			record.abortCount = obligation.getAbortCount();
			records.add(record);
		}
		return gson.toJson(records);
	}

	static void restore(Gson gson, String encoded, GeTradeLedger ledger)
	{
		if (gson == null || ledger == null || encoded == null || encoded.trim().isEmpty())
		{
			return;
		}

		Record[] records;
		try
		{
			records = gson.fromJson(encoded, Record[].class);
		}
		catch (RuntimeException ex)
		{
			return;
		}
		if (records == null)
		{
			return;
		}

		int restored = 0;
		for (Record record : records)
		{
			if (record == null || restored >= MAX_RECORDS)
			{
				continue;
			}
			try
			{
				GeTradeSide side = GeTradeSide.valueOf(record.side == null ? "" : record.side);
				if (side == GeTradeSide.BUY)
				{
					ledger.reserveBuy(
						record.id,
						record.slot,
						record.itemId,
						record.itemName,
						record.quantity,
						record.price,
						record.targetSellPrice);
				}
				else if (side == GeTradeSide.SELL)
				{
					ledger.createSell(
						record.id,
						record.parentId,
						record.slot,
						record.itemId,
						record.itemName,
						record.quantity,
						record.price);
				}
				else
				{
					continue;
				}

				if (record.placedAtEpochMs >= 0L)
				{
					ledger.markPlaced(record.id, Instant.ofEpochMilli(record.placedAtEpochMs));
				}
				ledger.markFilled(record.id, record.filledQuantity);
				for (int i = 0; i < Math.min(MAX_ABORT_COUNT, Math.max(0, record.abortCount)); i++)
				{
					ledger.incrementAbortCount(record.id);
				}
				restored++;
			}
			catch (RuntimeException ignored)
			{
				// Ignore malformed or stale persisted entries and continue with valid records.
			}
		}
	}

	private static final class Record
	{
		private String id;
		private int slot;
		private String side;
		private int itemId;
		private String itemName;
		private int quantity;
		private int price;
		private String parentId;
		private int targetSellPrice;
		private long placedAtEpochMs;
		private int filledQuantity;
		private int abortCount;
	}
}
