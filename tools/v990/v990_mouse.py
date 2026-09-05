from __future__ import annotations
import time, uuid
from v990_core import V990Error, validate_snapshot, require_bounds, safe_target_point, canvas_to_screen, contains, verify_mouse_event

class V990MouseAuthority:
    def __init__(self, bridge, input_driver, poll_interval=0.025, max_polls=20):
        self.bridge=bridge; self.input=input_driver; self.poll_interval=float(poll_interval); self.max_polls=max(1,int(max_polls))
        self._completed=set()

    def _fetch(self, instance):
        s=validate_snapshot(self.bridge.fetch(force=True))
        if s.get('bridgeInstanceId') != instance:
            raise V990Error('BRIDGE_RESTARTED','bridge instance changed during mouse transaction')
        return s

    @staticmethod
    def _focus_gate(s):
        m=s.get('mouse') or {}
        if not m.get('clientWindowFocused'): raise V990Error('WINDOW_NOT_ACTIVE','RuneLite window not focused')
        if not m.get('canvasFocused'): raise V990Error('CANVAS_NOT_FOCUSED','RuneLite canvas not focused')
        if int(m.get('currentButton') or 0) not in (0,-1): raise V990Error('MOUSE_BUTTON_STUCK',f"button={m.get('currentButton')}")

    def _wait_cursor(self, instance, b):
        last=None
        for _ in range(self.max_polls):
            s=self._fetch(instance); last=s; self._focus_gate(s)
            m=s.get('mouse') or {}
            if m.get('insideCanvas') and contains(b,m.get('canvasX'),m.get('canvasY')): return s
            if self.poll_interval: time.sleep(self.poll_interval)
        raise V990Error('MOUSE_TARGET_MISS',f"last={((last or {}).get('mouse') or {}).get('canvasX')},{((last or {}).get('mouse') or {}).get('canvasY')}")

    def _wait_event(self, instance, typ, button, b, after_seq, code):
        for _ in range(self.max_polls):
            s=self._fetch(instance); self._focus_gate(s)
            for e in (s.get('mouse') or {}).get('recentEvents') or []:
                if int(e.get('eventSeq') or 0) <= int(after_seq):
                    continue
                et=str(e.get('type','')).upper()
                if et in {'PRESS','WHEEL'} and not contains(b,e.get('canvasX'),e.get('canvasY')):
                    raise V990Error('MANUAL_MOUSE_HOLD',f'unexpected {et} outside target')
            if verify_mouse_event(s,typ,button,b,after_seq): return s
            if self.poll_interval: time.sleep(self.poll_interval)
        raise V990Error(code,f'{typ} not observed after eventSeq {after_seq}')

    def click(self, b, target_kind, button='left', action_id=None, duration=0.08):
        b=require_bounds(b,'MOUSE_TARGET_BOUNDS_INVALID')
        action_id=action_id or uuid.uuid4().hex
        if action_id in self._completed: raise V990Error('MOUSE_DUPLICATE_ACTION_BLOCKED',action_id)
        pre=validate_snapshot(self.bridge.fetch(force=True)); instance=pre['bridgeInstanceId']; self._focus_gate(pre)
        pre_seq=int((pre.get('mouse') or {}).get('eventSeq') or 0)
        point=safe_target_point(b); screen=canvas_to_screen(pre,point)
        self.input.moveTo(screen[0],screen[1],duration=duration)
        moved=self._wait_cursor(instance,b)
        move_seq=int((moved.get('mouse') or {}).get('eventSeq') or pre_seq)
        btn_num=1 if button=='left' else 3 if button=='right' else 2
        self.input.mouseDown(button=button)
        pressed=self._wait_event(instance,'PRESS',btn_num,b,move_seq,'MOUSE_PRESS_NOT_OBSERVED')
        press_seq=int((pressed.get('mouse') or {}).get('eventSeq') or move_seq)
        self.input.mouseUp(button=button)
        released=self._wait_event(instance,'RELEASE',btn_num,b,press_seq,'MOUSE_RELEASE_NOT_OBSERVED')
        self._completed.add(action_id)
        return {'status':'VERIFIED','actionId':action_id,'targetKind':target_kind,'targetCanvas':point,'targetScreen':screen,'snapshotSeq':released.get('snapshotSeq')}
