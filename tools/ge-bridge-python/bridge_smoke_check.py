from runelite_bridge import RuneLiteBridgeClient


def _bounds_text(bounds) -> str:
    if bounds is None or not bounds.valid:
        return "INVALID"
    return f"{bounds.x},{bounds.y} {bounds.width}x{bounds.height}"


def main() -> int:
    client = RuneLiteBridgeClient()
    snapshot = client.read_state()
    if snapshot is None:
        print("GE BRIDGE V3: WAIT/UNAVAILABLE")
        return 1

    print(
        f"GE BRIDGE V3: CONNECTED | tick={snapshot.tick} | world={snapshot.client.world} | "
        f"members={snapshot.client.members_world} | GP={snapshot.inventory_gp:,}"
    )
    print(
        f"CANVAS {snapshot.client.canvas_width}x{snapshot.client.canvas_height} | "
        f"VIEWPORT {snapshot.client.viewport_width}x{snapshot.client.viewport_height} "
        f"@ {snapshot.client.viewport_x_offset},{snapshot.client.viewport_y_offset}"
    )
    print(
        f"PLAYER {snapshot.player.world_x},{snapshot.player.world_y},{snapshot.player.plane} | "
        f"INV free={snapshot.inventory_state.free_slots}/{snapshot.inventory_state.capacity}"
    )
    print(
        f"SAFETY ready={snapshot.safety.bridge_ready} blocker={snapshot.safety.modal_blocker} "
        f"mouse={snapshot.safety.safe_for_mouse_actions} geMouse={snapshot.safety.safe_for_ge_mouse_actions}"
    )
    print(
        f"INPUT idle={snapshot.input.input_idle_ms}ms mouse={snapshot.input.mouse_x},{snapshot.input.mouse_y} "
        f"inside={snapshot.input.mouse_inside_canvas} buttons=0x{snapshot.input.mouse_buttons_down_mask:X} "
        f"lastButton={snapshot.input.last_mouse_button} wheel={snapshot.input.last_wheel_rotation} "
        f"control={snapshot.input.last_control_key or '--'}"
    )
    print(
        f"INPUT TIMES last={snapshot.input.last_input_epoch_ms} move={snapshot.input.last_mouse_move_epoch_ms} "
        f"click={snapshot.input.last_mouse_click_epoch_ms} keyboard={snapshot.input.last_keyboard_epoch_ms}"
    )
    print(
        f"GE open={snapshot.ge.open} setup={snapshot.ge.offer_setup_open} "
        f"item={snapshot.ge.offer_setup_item_id} | window={_bounds_text(snapshot.ge.window_bounds)} | "
        f"setupBounds={_bounds_text(snapshot.ge.offer_setup_bounds)} | "
        f"inventoryBounds={_bounds_text(snapshot.ge.inventory_bounds)}"
    )
    for slot in snapshot.slots[:3]:
        print(
            f"S{slot.slot + 1} | {slot.state} | {slot.visual} | "
            f"item={slot.item_id} | {slot.quantity_traded}/{slot.total_quantity} | "
            f"collect={slot.collect_ready}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
