import json
import time
import unittest
from unittest.mock import Mock

import requests

from runelite_bridge import RuneLiteBridgeClient


class RuneLiteBridgeClientTest(unittest.TestCase):
    def setUp(self):
        self.session = Mock()
        self.client = RuneLiteBridgeClient(session=self.session)

    def _response(self, payload):
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = payload
        return response

    def test_valid_snapshot_parses_exact_state_and_inventory(self):
        now = int(time.time() * 1000)
        self.session.get.return_value = self._response({
            "protocol": 1,
            "generatedAtEpochMs": now,
            "gameState": "LOGGED_IN",
            "slots": [{
                "slot": 0,
                "itemId": 314,
                "state": "BUYING",
                "visual": "ORANGE",
                "price": 12,
                "totalQuantity": 1000,
                "quantityTraded": 420,
                "spent": 5040,
                "collectReady": False,
            }],
            "inventory": [
                {"itemId": 995, "quantity": 53000},
                {"itemId": 314, "quantity": 100},
            ],
            "inventoryGp": 53000,
        })

        snapshot = self.client.read_state()
        self.assertIsNotNone(snapshot)
        self.assertEqual("BUYING", snapshot.slots[0].state)
        self.assertEqual("ORANGE", snapshot.slots[0].visual)
        self.assertEqual(53000, snapshot.inventory_gp)
        self.assertEqual(100, snapshot.inventory[314])

    def test_stale_snapshot_is_rejected(self):
        old = int((time.time() - 10) * 1000)
        self.session.get.return_value = self._response({
            "protocol": 1,
            "generatedAtEpochMs": old,
            "gameState": "LOGGED_IN",
            "slots": [],
            "inventory": [],
            "inventoryGp": 0,
        })
        self.assertIsNone(self.client.read_state())

    def test_logged_out_snapshot_is_rejected(self):
        now = int(time.time() * 1000)
        self.session.get.return_value = self._response({
            "protocol": 1,
            "generatedAtEpochMs": now,
            "gameState": "LOGIN_SCREEN",
            "slots": [],
            "inventory": [],
            "inventoryGp": 0,
        })
        self.assertIsNone(self.client.read_state())

    def test_protocol_mismatch_is_rejected(self):
        now = int(time.time() * 1000)
        self.session.get.return_value = self._response({
            "protocol": 2,
            "generatedAtEpochMs": now,
            "gameState": "LOGGED_IN",
            "slots": [],
            "inventory": [],
            "inventoryGp": 0,
        })
        self.assertIsNone(self.client.read_state())

    def test_unavailable_bridge_returns_none(self):
        self.session.get.side_effect = requests.RequestException("offline")
        self.assertIsNone(self.client.read_state())

    def test_slot_visual_fails_closed_when_state_missing(self):
        self.assertEqual("UNKNOWN", self.client.slot_visual(None, 0))


if __name__ == "__main__":
    unittest.main()
