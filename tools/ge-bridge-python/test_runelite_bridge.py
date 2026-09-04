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

    def _valid_payload(self):
        now = int(time.time() * 1000)
        return {
            "protocol": 3,
            "generatedAtEpochMs": now,
            "tick": 42,
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
            "client": {
                "loggedIn": True,
                "world": 301,
                "worldTypes": [],
                "membersWorld": False,
                "canvasWidth": 773,
                "canvasHeight": 535,
                "viewportWidth": 765,
                "viewportHeight": 503,
                "viewportXOffset": 4,
                "viewportYOffset": 4,
                "topLevelInterfaceId": 548,
                "fps": 50,
            },
            "player": {
                "present": True,
                "worldX": 3164,
                "worldY": 3487,
                "plane": 0,
            },
            "interfaces": {
                "grandExchangeOpen": True,
                "grandExchangeOfferSetupOpen": False,
                "bankOpen": False,
                "worldMapOpen": False,
                "dialogOpen": False,
                "chatboxInputOpen": False,
                "draggingWidget": False,
            },
            "ge": {
                "open": True,
                "offerSetupOpen": False,
                "offerSetupItemId": -1,
                "windowBounds": {"x": 20, "y": 20, "width": 500, "height": 360, "valid": True},
                "offerSetupBounds": {"x": -1, "y": -1, "width": 0, "height": 0, "valid": False},
                "inventoryBounds": {"x": 550, "y": 200, "width": 180, "height": 250, "valid": True},
            },
            "inventoryState": {
                "capacity": 28,
                "occupiedSlots": 3,
                "freeSlots": 25,
            },
            "safety": {
                "bridgeReady": True,
                "modalBlocker": False,
                "safeForMouseActions": True,
                "safeForGeMouseActions": True,
            },
            "input": {
                "lastInputEpochMs": now - 50,
                "lastMouseMoveEpochMs": now - 70,
                "lastMouseClickEpochMs": now - 100,
                "lastMousePressEpochMs": now - 110,
                "lastMouseReleaseEpochMs": now - 90,
                "lastMouseWheelEpochMs": now - 300,
                "lastKeyboardEpochMs": now - 500,
                "mouseX": 400,
                "mouseY": 250,
                "mouseInsideCanvas": True,
                "mouseButtonsDownMask": 0,
                "lastMouseButton": 1,
                "lastWheelRotation": -1,
                "lastControlKey": "SHIFT",
                "inputIdleMs": 50,
            },
        }

    def test_valid_snapshot_parses_protocol_v3_state_and_input(self):
        payload = self._valid_payload()
        self.session.get.return_value = self._response(payload)

        snapshot = self.client.read_state()
        self.assertIsNotNone(snapshot)
        self.assertEqual(42, snapshot.tick)
        self.assertEqual("BUYING", snapshot.slots[0].state)
        self.assertEqual(53000, snapshot.inventory_gp)
        self.assertEqual((773, 535), self.client.canvas_size(snapshot))
        self.assertEqual(25, snapshot.inventory_state.free_slots)
        self.assertTrue(self.client.safe_for_ge_mouse_actions(snapshot))
        self.assertEqual(20, self.client.ge_window_bounds(snapshot).x)
        self.assertEqual((400, 250), self.client.mouse_position(snapshot))
        self.assertEqual("SHIFT", snapshot.input.last_control_key)
        self.assertEqual(50, self.client.input_idle_ms(snapshot))

    def test_recent_input_helper_uses_bridge_timestamp(self):
        payload = self._valid_payload()
        self.session.get.return_value = self._response(payload)
        snapshot = self.client.read_state()
        self.assertIsNotNone(snapshot)
        self.assertTrue(self.client.recent_input(snapshot, 500, now_epoch_ms=payload["generatedAtEpochMs"]))
        self.assertFalse(self.client.recent_input(snapshot, 10, now_epoch_ms=payload["generatedAtEpochMs"]))

    def test_parser_does_not_expose_typed_text(self):
        payload = self._valid_payload()
        payload["input"]["typedText"] = "secret message"
        payload["input"]["keyChar"] = "x"
        self.session.get.return_value = self._response(payload)
        snapshot = self.client.read_state()
        self.assertIsNotNone(snapshot)
        self.assertFalse(hasattr(snapshot.input, "typed_text"))
        self.assertFalse(hasattr(snapshot.input, "key_char"))

    def test_missing_input_section_is_rejected(self):
        payload = self._valid_payload()
        del payload["input"]
        self.session.get.return_value = self._response(payload)
        self.assertIsNone(self.client.read_state())

    def test_modal_blocker_is_parsed_and_blocks_ge_safety(self):
        payload = self._valid_payload()
        payload["interfaces"]["bankOpen"] = True
        payload["safety"]["modalBlocker"] = True
        payload["safety"]["safeForMouseActions"] = False
        payload["safety"]["safeForGeMouseActions"] = False
        self.session.get.return_value = self._response(payload)

        snapshot = self.client.read_state()
        self.assertIsNotNone(snapshot)
        self.assertTrue(self.client.modal_blocked(snapshot))
        self.assertFalse(self.client.safe_for_mouse_actions(snapshot))
        self.assertFalse(self.client.safe_for_ge_mouse_actions(snapshot))

    def test_invalid_ge_bounds_fail_closed_helper(self):
        payload = self._valid_payload()
        payload["ge"]["windowBounds"]["valid"] = False
        self.session.get.return_value = self._response(payload)

        snapshot = self.client.read_state()
        self.assertIsNotNone(snapshot)
        self.assertIsNone(self.client.ge_window_bounds(snapshot))

    def test_stale_snapshot_is_rejected(self):
        payload = self._valid_payload()
        payload["generatedAtEpochMs"] = int((time.time() - 10) * 1000)
        self.session.get.return_value = self._response(payload)
        self.assertIsNone(self.client.read_state())

    def test_logged_out_snapshot_is_rejected(self):
        payload = self._valid_payload()
        payload["gameState"] = "LOGIN_SCREEN"
        self.session.get.return_value = self._response(payload)
        self.assertIsNone(self.client.read_state())

    def test_protocol_v2_is_rejected(self):
        payload = self._valid_payload()
        payload["protocol"] = 2
        self.session.get.return_value = self._response(payload)
        self.assertIsNone(self.client.read_state())

    def test_not_ready_safety_is_rejected(self):
        payload = self._valid_payload()
        payload["safety"]["bridgeReady"] = False
        self.session.get.return_value = self._response(payload)
        self.assertIsNone(self.client.read_state())

    def test_unavailable_bridge_returns_none(self):
        self.session.get.side_effect = requests.RequestException("offline")
        self.assertIsNone(self.client.read_state())

    def test_slot_visual_fails_closed_when_state_missing(self):
        self.assertEqual("UNKNOWN", self.client.slot_visual(None, 0))


if __name__ == "__main__":
    unittest.main()
