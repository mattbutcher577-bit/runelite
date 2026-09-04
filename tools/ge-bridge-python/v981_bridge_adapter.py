from __future__ import annotations

from typing import Optional

from runelite_bridge import BridgeSnapshot, RuneLiteBridgeClient


class V981RuneLiteStateAdapter:
    """Small compatibility layer for V981-style 3-slot state/GP reads.

    This module deliberately does not click, type, place offers, abort offers,
    collect offers, or change the existing F8 emergency stop. It only replaces
    visual/OCR observations with validated RuneLite bridge data.
    """

    def __init__(self, client: Optional[RuneLiteBridgeClient] = None) -> None:
        self.client = client or RuneLiteBridgeClient()
        self._snapshot: Optional[BridgeSnapshot] = None

    def refresh(self) -> bool:
        self._snapshot = self.client.read_state()
        return self._snapshot is not None

    def physical_status(self, slot_index: int) -> str:
        """Return GREEN/ORANGE/RED/EMPTY, or UNKNOWN when bridge state is unsafe."""
        return self.client.slot_visual(self._snapshot, slot_index)

    def exact_state(self, slot_index: int) -> str:
        """Return exact RuneLite offer state such as BUYING/BOUGHT/SOLD."""
        return self.client.slot_state(self._snapshot, slot_index)

    def inventory_gp(self) -> Optional[int]:
        """Return exact inventory coin quantity, or None when state is unsafe."""
        if self._snapshot is None:
            return None
        return self._snapshot.inventory_gp

    def inventory_quantity(self, item_id: int) -> Optional[int]:
        if self._snapshot is None:
            return None
        return self._snapshot.inventory.get(int(item_id), 0)

    def collect_ready(self, slot_index: int) -> Optional[bool]:
        if self._snapshot is None:
            return None
        for slot in self._snapshot.slots:
            if slot.slot == int(slot_index):
                return slot.collect_ready
        return None


# Suggested V981 integration pattern:
#
# bridge = V981RuneLiteStateAdapter()
#
# At the start of each fast state loop:
#     bridge.refresh()
#
# Replace authoritative physical status reads with:
#     status = bridge.physical_status(slot_index)
#     if status == "UNKNOWN":
#         # WAIT / block new offer. Do not treat UNKNOWN as EMPTY.
#
# Replace authoritative inventory GP OCR with:
#     gp = bridge.inventory_gp()
#     if gp is None:
#         # WAIT / fail closed.
#
# Existing OCR may remain for diagnostics only and must not override bridge data.
