/* ManaVault collection quantity safety, v5.12.15.
 * Independently protects Collection +/- controls, including filtered lists.
 * Only deliberate confirmation may remove the final physical copy.
 * Recent decrements remain undoable across re-renders and app restarts.
 */
(function(root){
 'use strict';
 const KEY='mv_collection_quantity_undo_v1', LIMIT=20;
 let history=[];
 const pending=new Set();
 try { const read=JSON.parse(localStorage.getItem(KEY)||'[]');
  if(Array.isArray(read))history=read.filter(x=>x&&x.version===1&&typeof x.id==='string'&&x.before&&Number.isFinite(x.afterQty)).slice(-LIMIT);
 }catch(_){history=[];}
 const byId=id=>state.cards.find(c=>String(c.id)===String(id));
 function persistHistory(){try{localStorage.setItem(KEY,JSON.stringify(history.slice(-LIMIT)));}catch(_){/* in-memory undo remains available */}}
 function label(card){return String(card?.name||'Unnamed card')+' · '+String(card?.set_name||card?.set||'Unknown edition')+(card?.collector_number?' #'+card.collector_number:'');}
 function last(){return history.length?history[history.length-1]:null;}
 function undoPanel(){const x=last();return x?'<div class="mv-quantity-undo-panel" role="status"><span>Recently removed 1 × '+esc(x.before.name||'card')+(x.afterQty===0?' (final copy)':'')+'.</span><button type="button" id="mvUndoCollectionQty">Undo removal</button></div>':'';}
 function refreshToast(text){
  if(typeof document==='undefined'||!document.body)return;
  let bar=document.getElementById('mvCollectionQuantityUndoToast');
  if(!bar){bar=document.createElement('div');bar.id='mvCollectionQuantityUndoToast';bar.className='mv-quantity-toast';bar.setAttribute('role','status');bar.setAttribute('aria-live','polite');document.body.appendChild(bar);}
  bar.replaceChildren();
  const description=document.createElement('span');description.textContent=text;bar.appendChild(description);
  if(last()){const btn=document.createElement('button');btn.type='button';btn.textContent='Undo';btn.onclick=undo;bar.appendChild(btn);}
  const close=document.createElement('button');close.type='button';close.className='mv-quantity-toast-close';close.setAttribute('aria-label','Dismiss message');close.textContent='×';close.onclick=()=>bar.remove();bar.appendChild(close);
 }
 function askLastCopy(card){
  if(typeof document==='undefined'||!document.body)return Promise.resolve(false);
  return new Promise(resolve=>{
   const previous=document.activeElement, overlay=document.createElement('div');overlay.className='mv-quantity-confirm';
   overlay.innerHTML='<section class="mv-quantity-dialog" role="dialog" aria-modal="true" aria-labelledby="mvQuantityConfirmTitle" aria-describedby="mvQuantityConfirmDescription"><h2 id="mvQuantityConfirmTitle">Remove the last copy?</h2><p id="mvQuantityConfirmDescription"></p><p class="small muted">This removes the card from your collection inventory. You can undo the removal afterward.</p><div class="flex mv-quantity-dialog-actions"><button type="button" class="primary" data-quantity-cancel>Keep card</button><button type="button" class="warn" data-quantity-remove>Remove final copy</button></div></section>';
   overlay.querySelector('#mvQuantityConfirmDescription').textContent='You have only 1 copy of '+label(card)+'.';
   const cancel=overlay.querySelector('[data-quantity-cancel]'),remove=overlay.querySelector('[data-quantity-remove]');
   function end(accepted){document.removeEventListener('keydown',keys,true);overlay.remove();try{previous?.focus?.()}catch(_){}resolve(accepted)}
   function keys(event){if(event.key==='Escape'){event.preventDefault();end(false)}else if(event.key==='Tab'){
    if(event.shiftKey&&document.activeElement===cancel){event.preventDefault();remove.focus();}
    else if(!event.shiftKey&&document.activeElement===remove){event.preventDefault();cancel.focus();}
   }}
   cancel.onclick=()=>end(false);remove.onclick=()=>end(true);
   overlay.onclick=event=>{if(event.target===overlay)end(false)};
   document.body.appendChild(overlay);document.addEventListener('keydown',keys,true);cancel.focus();
  });
 }
 async function decrement(id,confirmLast=askLastCopy){
  id=String(id);
  if(pending.has(id))return 'busy';
  const previous=byId(id);
  if(!previous)return 'missing';
  const originalQty=Number(previous.qty);
  if(!Number.isInteger(originalQty)||originalQty<=0)return 'invalid';
  pending.add(id);
  try {
   if(originalQty===1&&!(await confirmLast({...previous})))return 'cancelled';
   const current=byId(id);
   if(!current)return 'missing';
   const qty=Number(current.qty);
   if(!Number.isInteger(qty)||qty<=0)return 'invalid';
   // If a separate action has already changed this copy, do not apply a
   // stale confirmation to the changed collection quantity.
   if(qty!==originalQty)return 'changed';
   const before=JSON.parse(JSON.stringify(current));
   const index=state.cards.indexOf(current),afterQty=qty-1;
   if(afterQty===0)state.cards.splice(index,1);else current.qty=afterQty;
   const record={version:1,id,afterQty,index,before,at:Date.now()};
   history.push(record);if(history.length>LIMIT)history.shift();
   try{save();}catch(error){ // No destructive mutation if persistence fails.
    history.pop();if(afterQty===0)state.cards.splice(Math.min(index,state.cards.length),0,before);
    else current.qty=qty;
    try{render()}catch(_){}
    refreshToast('Could not save your change. The card was not removed.');
    return 'error';
   }
   persistHistory();refreshToast(afterQty===0?'Removed the last copy of '+before.name+'.':'Reduced '+before.name+' to '+afterQty+' copies.');
   return 'removed';
  }finally{pending.delete(id)}
 }
 function undo(){
  const record=last();if(!record)return false;
  const now=byId(record.id);
  // A later import, edit or scan may have changed the same entry. Never
  // overwrite such data with a stale snapshot.
  if(record.afterQty>0 && (!now||!Number.isInteger(Number(now.qty))||Number(now.qty)<record.afterQty)){
   refreshToast('Collection changed since this removal; undo was not applied.');return false;
  }
  if(now)now.qty=Number(now.qty)+1;
  else state.cards.splice(Math.min(Math.max(record.index,0),state.cards.length),0,JSON.parse(JSON.stringify(record.before)));
  history.pop();
  try{save()}catch(error){ // Roll back the tentative undo if write fails.
   if(now)now.qty=Number(now.qty)-1;else state.cards=state.cards.filter(c=>c.id!==record.id);
   history.push(record);refreshToast('Could not restore card: storage unavailable.');return false;
  }
  persistHistory();refreshToast('Restored one copy of '+record.before.name+'.');return true;
 }
 function clearHistory(){history=[];pending.clear();persistHistory();document.getElementById('mvCollectionQuantityUndoToast')?.remove();}
 function bind(){
  document.querySelectorAll('[data-inc]').forEach(button=>button.onclick=()=>{
   const c=byId(button.dataset.inc);if(!c)return;
   const n=Number(c.qty);if(!Number.isInteger(n)||n<0)return;
   c.qty=n+1;save();
  });
  document.querySelectorAll('[data-dec]').forEach(button=>button.onclick=()=>{void decrement(button.dataset.dec)});
  document.querySelectorAll('[data-trade]').forEach(button=>button.onclick=()=>{
   if(!state.trades.includes(button.dataset.trade))state.trades.push(button.dataset.trade);save();
  });
  const btn=document.getElementById('mvUndoCollectionQty');if(btn)btn.onclick=undo;
 }
 root.mvCollectionSafety={bind,decrement,undo,undoPanel,clearHistory,askLastCopy,last};
})(window);
