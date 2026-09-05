from __future__ import annotations
import math, time

class V990Error(RuntimeError):
    def __init__(self, code, message=''):
        self.code=str(code); self.message=str(message or code)
        super().__init__(f'{self.code}: {self.message}')

def _i(v,d=0):
    try: return int(v)
    except Exception: return d

def _f(v,d=0.0):
    try: return float(v)
    except Exception: return d

def validate_snapshot(s, max_age_ms=2500, now_ms=None):
    if not isinstance(s,dict): raise V990Error('BRIDGE_OFFLINE','snapshot unavailable')
    if _i(s.get('protocol')) != 5: raise V990Error('PROTOCOL_MISMATCH',f"expected 5 got {s.get('protocol')}")
    if not s.get('bridgeInstanceId'): raise V990Error('BRIDGE_SESSION_INVALID','bridgeInstanceId missing')
    if _i(s.get('snapshotSeq'),-1) < 0: raise V990Error('BRIDGE_SESSION_INVALID','snapshotSeq missing')
    now_ms=_i(now_ms, int(time.time()*1000))
    generated=_i(s.get('generatedAtEpochMs'),0)
    if generated <= 0 or now_ms-generated > max_age_ms: raise V990Error('BRIDGE_STALE',f'age={max(0,now_ms-generated)}ms')
    if s.get('gameState')!='LOGGED_IN': raise V990Error('GAME_NOT_LOGGED_IN',str(s.get('gameState')))
    c=s.get('client') or {}
    if not c.get('loggedIn'): raise V990Error('GAME_NOT_LOGGED_IN','client.loggedIn false')
    if c.get('membersWorld'): raise V990Error('WORLD_NOT_F2P','members world')
    if not c.get('loginSettled'): raise V990Error('LOGIN_RESYNC','login not settled')
    if not c.get('canvasScreenPositionValid'): raise V990Error('MOUSE_COORDINATE_DESYNC','canvas screen origin invalid')
    for k in ('canvasWidth','canvasHeight','realWidth','realHeight'):
        if _i(c.get(k),0)<=0: raise V990Error('ACTION_BOUNDS_INVALID',f'client.{k} invalid')
    safety=s.get('safety') or {}
    if not safety.get('bridgeReady'): raise V990Error('BRIDGE_STALE','safety.bridgeReady false')
    for section in ('slots','search','geInput','geActions','geInventory','mouse'):
        if section not in s: raise V990Error('BRIDGE_STATE_INCOMPLETE',f'{section} missing')
    return s

def valid_bounds(b):
    return isinstance(b,dict) and bool(b.get('valid')) and _i(b.get('width'))>0 and _i(b.get('height'))>0

def require_bounds(b, code='ACTION_BOUNDS_INVALID'):
    if not valid_bounds(b): raise V990Error(code,'invalid RuneLite bounds')
    return b

def contains(b,x,y):
    if not valid_bounds(b): return False
    return _i(b['x']) <= _i(x) < _i(b['x'])+_i(b['width']) and _i(b['y']) <= _i(y) < _i(b['y'])+_i(b['height'])

def safe_target_point(b, inset_ratio=.18, min_inset=2):
    b=require_bounds(b,'MOUSE_TARGET_BOUNDS_INVALID')
    x,y,w,h=map(_i,(b['x'],b['y'],b['width'],b['height']))
    ix=min(max(min_inset,int(w*inset_ratio)), max(0,(w-2)//2))
    iy=min(max(min_inset,int(h*inset_ratio)), max(0,(h-2)//2))
    left,right=x+ix,x+w-ix-1; top,bottom=y+iy,y+h-iy-1
    if right<left or bottom<top: raise V990Error('MOUSE_TARGET_BOUNDS_INVALID','interior collapsed')
    return ((left+right)//2,(top+bottom)//2)

def canvas_to_screen(s, point):
    validate_snapshot(s)
    c=s['client']; x,y=map(_i,point)
    cw,ch=_i(c['canvasWidth']),_i(c['canvasHeight'])
    if x<0 or y<0 or x>=cw or y>=ch: raise V990Error('MOUSE_OUTSIDE_CANVAS',f'{point} outside {cw}x{ch}')
    # RuneLite widget/mouse bounds are already in real canvas coordinates. The exact
    # canvas screen origin is authoritative; stretched dimensions are retained for
    # diagnostics/calibration and are not applied a second time here.
    return (_i(c['canvasScreenX'])+x,_i(c['canvasScreenY'])+y)

def find_slot(s, slot_one_based):
    target=_i(slot_one_based)-1
    for row in s.get('slots') or []:
        if _i(row.get('slot'),-99)==target: return row
    raise V990Error('SLOT_IDENTITY_CHANGED',f'slot {slot_one_based} missing')

def find_action_slot(s, slot_one_based):
    target=_i(slot_one_based)-1
    for row in (s.get('geActions') or {}).get('slots') or []:
        if _i(row.get('slot'),-99)==target: return row
    raise V990Error('ACTION_BOUNDS_INVALID',f'action slot {slot_one_based} missing')

def find_search_result(s,item_id):
    search=s.get('search') or {}
    if not search.get('open'): raise V990Error('SEARCH_RESULTS_EMPTY','search closed')
    for r in search.get('results') or []:
        if _i(r.get('itemId'),-1)==_i(item_id): return r
    raise V990Error('SEARCH_ITEM_ID_NOT_FOUND',f'itemId={item_id}')

def find_ge_inventory_entry(s,item_id,minimum_quantity=1):
    gi=s.get('geInventory') or {}
    if not gi.get('open'): raise V990Error('GE_INVENTORY_NOT_UPDATED','GE inventory closed')
    matches=[]
    for e in gi.get('entries') or []:
        if _i(e.get('canonicalItemId'),-1)==_i(item_id) or _i(e.get('rawItemId'),-1)==_i(item_id):
            if _i(e.get('quantity'),0)>=_i(minimum_quantity,1): matches.append(e)
    if not matches: raise V990Error('SELL_ITEM_NOT_FOUND',f'itemId={item_id} qty>={minimum_quantity}')
    matches.sort(key=lambda e:(-_i(e.get('quantity')), _i(e.get('inventorySlot'),999)))
    return matches[0]

def verify_mouse_event(s,event_type,button,bounds,after_seq):
    require_bounds(bounds,'MOUSE_TARGET_BOUNDS_INVALID')
    events=(s.get('mouse') or {}).get('recentEvents') or []
    for e in events:
        if _i(e.get('eventSeq'),0)<=_i(after_seq): continue
        if str(e.get('type','')).upper()!=str(event_type).upper(): continue
        if _i(e.get('button'),0)!=_i(button): continue
        if contains(bounds,e.get('canvasX'),e.get('canvasY')): return True
    return False

def verify_offer_confirmation(s,slot_one_based,item_id,qty,price,allowed_states):
    row=find_slot(s,slot_one_based)
    st=str(row.get('state','')).upper()
    if st not in set(allowed_states): raise V990Error('CONFIRM_STATE_TIMEOUT',f'state={st}')
    if _i(row.get('itemId'),-1)!=_i(item_id): raise V990Error('CONFIRMED_ITEM_MISMATCH',f"got {row.get('itemId')}")
    if _i(row.get('totalQuantity'),0)!=_i(qty): raise V990Error('CONFIRMED_QUANTITY_MISMATCH',f"got {row.get('totalQuantity')}")
    if _i(row.get('price'),0)!=_i(price): raise V990Error('CONFIRMED_PRICE_MISMATCH',f"got {row.get('price')}")
    return True

def shadow_rank(features):
    profit=max(0.0,_f(features.get('profit_after_tax')))
    p=min(1.0,max(0.0,_f(features.get('fill_probability'),.5)))
    fill=max(1.0,_f(features.get('expected_fill_seconds'),60.0))
    downside=max(0.0,_f(features.get('downside')))
    capital=max(1.0,_f(features.get('capital'),1.0))
    volume=max(0.0,_f(features.get('volume')))
    expected=profit*p-downside*(1.0-p)
    time_factor=1.0/(1.0+fill/120.0)
    liquidity=math.log1p(volume)/10.0
    capital_eff=expected/capital
    score=expected*time_factor*(0.75+min(.5,liquidity))+capital_eff*100.0
    return {'mode':'SHADOW_ONLY','score':score,'expected_value':expected,'fill_probability':p,'expected_fill_seconds':fill}

def verify_setup_values(s,item_id,qty,price,offer_type):
    ge=s.get('ge') or {}
    if int(ge.get('offerSetupItemId',-1))!=int(item_id):
        raise V990Error('SELECTED_ITEM_MISMATCH',f"got {ge.get('offerSetupItemId')}")
    if int(ge.get('offerSetupQuantity',0))!=int(qty):
        raise V990Error('CONFIRMED_QUANTITY_MISMATCH',f"setup got {ge.get('offerSetupQuantity')}")
    if int(ge.get('offerSetupPrice',0))!=int(price):
        raise V990Error('CONFIRMED_PRICE_MISMATCH',f"setup got {ge.get('offerSetupPrice')}")
    if str(ge.get('offerSetupType','UNKNOWN')).upper()!=str(offer_type).upper():
        raise V990Error('GE_OFFER_TYPE_MISMATCH',f"setup got {ge.get('offerSetupType')}")
    return True
