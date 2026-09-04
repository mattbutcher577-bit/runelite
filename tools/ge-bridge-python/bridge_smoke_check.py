from runelite_bridge import RuneLiteBridgeClient


def main() -> int:
    client = RuneLiteBridgeClient()
    snapshot = client.read_state()
    if snapshot is None:
        print("GE BRIDGE: WAIT/UNAVAILABLE")
        return 1

    print(f"GE BRIDGE: CONNECTED | GP={snapshot.inventory_gp:,}")
    for slot in snapshot.slots[:3]:
        print(
            f"S{slot.slot + 1} | {slot.state} | {slot.visual} | "
            f"item={slot.item_id} | {slot.quantity_traded}/{slot.total_quantity} | "
            f"collect={slot.collect_ready}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
