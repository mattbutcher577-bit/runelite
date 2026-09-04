from __future__ import annotations
import time, uuid
from v990_core import (V990Error, validate_snapshot, require_bounds, find_slot, find_action_slot,
    find_search_result, find_ge_inventory_entry, verify_offer_confirmation, verify_setup_values)

class V990Executor:
    def __init__(self, bridge, mouse, keyboard, log=None, poll_interval=.05, max_polls=40, stop_check=None):
        self.bridge=bridge; self.mouse=mouse; self.keyboard=keyboard; self.log=log or (lambda x:None)
        self.poll_interval=float(poll_interval); self.max_polls=max(1,int(max_polls)); self.stop_check=stop_check or (lambda:None)
        self.abort_sent=set()

    def fetch(self): self.stop_check(); return validate_snapshot(self.bridge.fetch(force=True))
    def _wait(self,predicate,code,detail=''):
        last=None
        for _ in range(self.max_polls):
            self.stop_check(); s=self.fetch(); last=s
            try:
                if predicate(s): return s
            except V990Error: pass
            if self.poll_interval: time.sleep(self.poll_interval)
        raise V990Error(code,detail or f"last tick={((last or {}).get('tick'))}")
    def _click(self,b,kind): return self.mouse.click(require_bounds(b),kind,action_id=uuid.uuid4().hex)
    def _mode(self,s): return str((s.get('geInput') or {}).get('mode','UNKNOWN')).upper()
    def _type(self,text,required_mode,numeric=False):
        s=self.fetch(); mode=self._mode(s)
        if mode!=required_mode: raise V990Error('GE_INPUT_MODE_MISMATCH',f'expected {required_mode} got {mode}')
        self.keyboard.hotkey('ctrl','a'); self.keyboard.press('backspace')
        if numeric:
            for ch in str(int(text)): self.keyboard.press(ch)
        else:
            self.keyboard.write(str(text),interval=.02)
        if numeric: self.keyboard.press('enter')
        return True
    def _selected_item(self,s,item_id): return int((s.get('ge') or {}).get('offerSetupItemId',-1))==int(item_id)

    def buy(self,item):
        slot=max(1,min(3,int(getattr(item,'slot',1)))); item_id=int(getattr(item,'id',-1)); name=str(getattr(item,'name',''))
        qty=max(1,int(getattr(item,'quantity',1))); price=max(1,int(getattr(item,'buy',1)))
        if item_id<=0 or not name: raise V990Error('ORDER_INVALID','missing item id/name')
        pre=self.fetch(); row=find_slot(pre,slot)
        if str(row.get('state')).upper()!='EMPTY': raise V990Error('SLOT_NOT_EMPTY',f'S{slot}={row.get("state")}')
        if int(pre.get('inventoryGp',0)) < qty*price: raise V990Error('GP_UNAVAILABLE',f'need {qty*price}')
        action=find_action_slot(pre,slot); self._click(action.get('buyButton'),'BUY_SLOT')
        setup=self._wait(lambda s: bool((s.get('interfaces') or {}).get('grandExchangeOfferSetupOpen')),'GE_SETUP_TIMEOUT')
        if self._mode(setup)!='ITEM_SEARCH':
            self._click((setup.get('geActions') or {}).get('setupItem'),'SETUP_ITEM')
            setup=self._wait(lambda s:self._mode(s)=='ITEM_SEARCH','GE_INPUT_MODE_MISMATCH','ITEM_SEARCH not reached')
        pre_search_tick=int((setup.get('search') or {}).get('updatedTick',-1))
        self._type(name,'ITEM_SEARCH',numeric=False)
        search=self._wait(lambda s:int((s.get('search') or {}).get('updatedTick',-1))>=max(int(s.get('tick',0))-1,pre_search_tick) and bool((s.get('search') or {}).get('results')),
                          'SEARCH_NOT_UPDATED')
        result=find_search_result(search,item_id); rb=result.get('nameBounds') if (result.get('nameBounds') or {}).get('valid') else result.get('iconBounds')
        self._click(rb,'SEARCH_RESULT')
        selected=self._wait(lambda s:self._selected_item(s,item_id),'SELECTED_ITEM_MISMATCH')
        self._set_numeric(selected,'quantityButton','QUANTITY',qty,'BUY_QUANTITY')
        after_qty=self.fetch(); self._set_numeric(after_qty,'priceButton','PRICE',price,'BUY_PRICE')
        ready=self.fetch(); verify_setup_values(ready,item_id,qty,price,'BUY'); self._click((ready.get('geActions') or {}).get('confirm'),'BUY_CONFIRM')
        confirmed=self._wait(lambda s:self._confirmed_bool(s,slot,item_id,qty,price,{'BUYING','BOUGHT'}),'CONFIRM_STATE_TIMEOUT')
        verify_offer_confirmation(confirmed,slot,item_id,qty,price,{'BUYING','BOUGHT'})
        self.log(f'V990 BUY CONFIRMED | S{slot} | {name} | ID {item_id} | QTY {qty:,} | {price:,}gp')
        return True

    def _set_numeric(self,s,button_key,mode,value,kind):
        self._click((s.get('geActions') or {}).get(button_key),kind+'_OPEN')
        self._wait(lambda x:self._mode(x)==mode,'GE_INPUT_MODE_MISMATCH',f'{mode} not reached')
        self._type(value,mode,numeric=True)
        self._wait(lambda x:self._mode(x)=='NONE','GE_INPUT_MODE_MISMATCH',f'{mode} did not return to NONE')

    def _confirmed_bool(self,s,slot,item_id,qty,price,states):
        try: return verify_offer_confirmation(s,slot,item_id,qty,price,states)
        except V990Error: return False

    def sell(self,item):
        slot=max(1,min(3,int(getattr(item,'slot',1)))); item_id=int(getattr(item,'id',-1)); name=str(getattr(item,'name',''))
        desired=max(1,int(getattr(item,'quantity',1))); price=max(1,int(getattr(item,'sell',1)))
        pre=self.fetch(); row=find_slot(pre,slot)
        if str(row.get('state')).upper()!='EMPTY': raise V990Error('SLOT_NOT_EMPTY',f'S{slot}={row.get("state")}')
        self._click(find_action_slot(pre,slot).get('sellButton'),'SELL_SLOT')
        inv=self._wait(lambda s:bool((s.get('geInventory') or {}).get('open')),'GE_INVENTORY_NOT_UPDATED')
        entry=find_ge_inventory_entry(inv,item_id,1); available=int(entry.get('quantity',0)); qty=min(desired,available)
        if qty<=0: raise V990Error('SELL_ITEM_NOT_FOUND',f'{item_id}')
        self._click(entry.get('bounds'),'GE_INVENTORY_ITEM')
        selected=self._wait(lambda s:self._selected_item(s,item_id),'SELECTED_ITEM_MISMATCH')
        self._set_numeric(selected,'quantityButton','QUANTITY',qty,'SELL_QUANTITY')
        after_qty=self.fetch(); self._set_numeric(after_qty,'priceButton','PRICE',price,'SELL_PRICE')
        ready=self.fetch(); verify_setup_values(ready,item_id,qty,price,'SELL'); self._click((ready.get('geActions') or {}).get('confirm'),'SELL_CONFIRM')
        confirmed=self._wait(lambda s:self._confirmed_bool(s,slot,item_id,qty,price,{'SELLING','SOLD'}),'CONFIRM_STATE_TIMEOUT')
        verify_offer_confirmation(confirmed,slot,item_id,qty,price,{'SELLING','SOLD'})
        try: item.quantity=qty
        except Exception: pass
        self.log(f'V990 SELL CONFIRMED | S{slot} | {name} | ID {item_id} | QTY {qty:,} | {price:,}gp')
        return True

    def collect(self,slot_one_based,item_id=None):
        slot=int(slot_one_based); pre=self.fetch(); row=find_slot(pre,slot)
        if not bool(row.get('collectReady')): raise V990Error('COLLECT_STATE_MISMATCH',f'S{slot} not collectReady')
        before_state=str(row.get('state')); before_qty=self._inventory_qty(pre,item_id) if item_id else None
        self._click((pre.get('geActions') or {}).get('collect'),'COLLECT')
        def changed(s):
            try: new=find_slot(s,slot)
            except V990Error: return False
            if str(new.get('state'))!=before_state or not bool(new.get('collectReady')): return True
            return item_id is not None and self._inventory_qty(s,item_id)>before_qty
        self._wait(changed,'COLLECT_STATE_MISMATCH')
        self.log(f'V990 COLLECT VERIFIED | S{slot}')
        return True

    @staticmethod
    def _inventory_qty(s,item_id):
        total=0
        for e in s.get('inventory') or []:
            if int(e.get('itemId',-1))==int(item_id): total+=int(e.get('quantity',0))
        return total

    def abort_buy_once(self,slot_one_based,item_id):
        slot=int(slot_one_based); pre=self.fetch(); row=find_slot(pre,slot)
        if int(row.get('itemId',-1))!=int(item_id) or str(row.get('state')).upper()!='BUYING':
            raise V990Error('SLOT_IDENTITY_CHANGED',f'S{slot} not matching BUYING obligation')
        key=(pre.get('bridgeInstanceId'),slot,int(item_id),int(row.get('totalQuantity',0)),int(row.get('price',0)))
        if key in self.abort_sent: raise V990Error('MOUSE_DUPLICATE_ACTION_BLOCKED','one abort already sent')
        abort=(pre.get('geActions') or {}).get('abort')
        if not (abort or {}).get('valid'):
            self._click(find_action_slot(pre,slot).get('openButton'),'OPEN_OFFER')
            pre=self._wait(lambda s:bool(((s.get('geActions') or {}).get('abort') or {}).get('valid')),'ACTION_BOUNDS_INVALID','abort bound unavailable')
            abort=(pre.get('geActions') or {}).get('abort')
        self._click(abort,'ABORT_BUY'); self.abort_sent.add(key)
        self._wait(lambda s:str(find_slot(s,slot).get('state')).upper() in {'CANCELLED_BUY','BOUGHT'},'CONFIRM_STATE_TIMEOUT','abort transition missing')
        self.log(f'V990 ABORT VERIFIED | S{slot} | ID {item_id}')
        return True
