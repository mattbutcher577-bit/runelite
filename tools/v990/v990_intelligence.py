from __future__ import annotations
import json, time
from collections import defaultdict, deque
from v990_core import shadow_rank

class GeLimitLedger:
    def __init__(self, window_seconds=14400):
        self.window_seconds=float(window_seconds); self.events=defaultdict(deque)
    def _prune(self,item_id,now):
        q=self.events[int(item_id)]; cutoff=float(now)-self.window_seconds
        while q and q[0][0] < cutoff: q.popleft()
    def record_fill(self,item_id,quantity,when=None):
        when=time.time() if when is None else float(when); qty=max(0,int(quantity))
        if qty: self.events[int(item_id)].append((when,qty))
    def used(self,item_id,now=None):
        now=time.time() if now is None else float(now); self._prune(item_id,now)
        return sum(q for _,q in self.events[int(item_id)])
    def remaining(self,item_id,known_limit,now=None):
        return max(0,int(known_limit)-self.used(item_id,now))
    def to_jsonable(self): return {str(k):list(v) for k,v in self.events.items()}

class StreamingMarketModel:
    """Small online calibration model. Shadow-only; never authorises input."""
    def __init__(self, alpha=0.2):
        self.alpha=float(alpha); self.rows={}
    def _row(self,item_id):
        return self.rows.setdefault(int(item_id),{'a':1.0,'b':1.0,'fill_s':120.0,'profit':0.0,'downside':0.0,'n':0})
    def update(self,item_id,filled,fill_seconds=None,profit_after_tax=0,downside=0):
        r=self._row(item_id); r['n']+=1
        if filled: r['a']+=1.0
        else: r['b']+=1.0
        a=self.alpha
        if fill_seconds is not None and float(fill_seconds)>0:
            r['fill_s']=(1-a)*r['fill_s']+a*float(fill_seconds)
        r['profit']=(1-a)*r['profit']+a*float(profit_after_tax)
        r['downside']=(1-a)*r['downside']+a*max(0.0,float(downside))
    def predict(self,item_id,capital=1,volume=0):
        r=self._row(item_id); p=r['a']/(r['a']+r['b'])
        features={'profit_after_tax':r['profit'],'fill_probability':p,'expected_fill_seconds':r['fill_s'],'downside':r['downside'],'capital':max(1,float(capital)),'volume':max(0,float(volume))}
        out=shadow_rank(features); out.update({'itemId':int(item_id),'samples':r['n']})
        return out

_BLOCKED_KEYS={'query','typedText','keyChar','clipboard','username','password','chatText','loginText','messageText','searchText'}
def sanitize_trace(value):
    if isinstance(value,dict):
        return {k:sanitize_trace(v) for k,v in value.items() if k not in _BLOCKED_KEYS}
    if isinstance(value,list): return [sanitize_trace(v) for v in value]
    return value

class JsonlTraceWriter:
    def __init__(self,path): self.path=path
    def write(self,record):
        with open(self.path,'a',encoding='utf-8') as f:
            f.write(json.dumps(sanitize_trace(record),separators=(',',':'),ensure_ascii=False)+'\n')
