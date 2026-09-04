from __future__ import annotations
import json, time, urllib.request
from v990_core import V990Error, validate_snapshot

class V990BridgeClient:
    def __init__(self,url='http://127.0.0.1:17654/state',timeout=1.5,max_age_ms=2500):
        self.url=url; self.timeout=float(timeout); self.max_age_ms=int(max_age_ms); self._last_instance=None; self._last_seq=-1
    def fetch(self,force=True):
        try:
            with urllib.request.urlopen(self.url,timeout=self.timeout) as r:
                if getattr(r,'status',200)!=200: raise V990Error('BRIDGE_OFFLINE',f'HTTP {r.status}')
                s=json.loads(r.read().decode('utf-8'))
        except V990Error: raise
        except Exception as e: raise V990Error('BRIDGE_OFFLINE',f'{type(e).__name__}: {e}') from e
        validate_snapshot(s,self.max_age_ms)
        instance=s['bridgeInstanceId']; seq=int(s['snapshotSeq'])
        if self._last_instance==instance and seq < self._last_seq:
            raise V990Error('BRIDGE_SEQUENCE_REWIND',f'{seq} < {self._last_seq}')
        self._last_instance=instance; self._last_seq=seq
        return s
