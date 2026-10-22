/* ManaVault v4.0: Scryfall default_cards, all printings, automatic daily metadata checks.
   Two alternating IndexedDB stores protect the previous complete catalog if download fails.
   Records are keyed by Scryfall printing ID, NOT by normalized name. */
(function(){
'use strict';
const DB='manavault_printings_v40', META='meta', STORE_A='printings_a', STORE_B='printings_b';
const DAY=86400000, MIN_PRINTINGS=100000;
let opening, running=null, lastStatus='';
let maintenanceAllowed=false;
function open(){return opening ||= new Promise((resolve,reject)=>{
 const r=indexedDB.open(DB,2);
 r.onupgradeneeded=()=>{
  const db=r.result;
  for(const name of [STORE_A,STORE_B]){
   const st=db.objectStoreNames.contains(name)?r.transaction.objectStore(name):db.createObjectStore(name,{keyPath:'id'});
   // Upgrade indexes on existing fields without erasing a downloaded catalog.
   for(const [key,field] of Object.entries({nameKey:'nameKey',setCollector:'setCollector',setCode:'set',
        collectorNumber:'collector_number',illustration:'illustration_id',oracle:'oracle_id',setName:'set_name'})){
    if(!st.indexNames.contains(key))st.createIndex(key,field,{unique:false});
   }
  }
  if(!db.objectStoreNames.contains(META))db.createObjectStore(META);
 };
 r.onsuccess=()=>resolve(r.result);r.onerror=()=>{opening=null;reject(r.error)};
 r.onblocked=()=>console.warn('Printing catalog upgrade blocked by another app view');
})}

function norm(s){return String(s||'').normalize('NFKD').toLowerCase().replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim()}
function record(c){return {id:c.id,nameKey:norm(c.name),name:c.name,oracle_id:c.oracle_id||'',illustration_id:c.illustration_id||c.card_faces?.[0]?.illustration_id||'',illustration_id_back:c.card_faces?.[1]?.illustration_id||'',image_normal_back:c.card_faces?.[1]?.image_uris?.normal||'',set:c.set||'',set_name:c.set_name||'',collector_number:c.collector_number||'',setCollector:(c.set||'').toLowerCase()+'#'+String(c.collector_number||'').toLowerCase(),type_line:c.type_line||'',colors:c.colors||[],color_identity:c.color_identity||[],mana_cost:c.mana_cost||'',rarity:c.rarity||'',layout:c.layout||'',frame:c.frame||'',border_color:c.border_color||'',full_art:!!c.full_art,textless:!!c.textless,promo:!!c.promo,security_stamp:c.security_stamp||'',finishes:c.finishes||[],frame_effects:c.frame_effects||[],released_at:c.released_at||'',image_url:c.image_uris?.art_crop||c.card_faces?.[0]?.image_uris?.art_crop||'',image_small:c.image_uris?.small||c.card_faces?.[0]?.image_uris?.small||'',image_normal:c.image_uris?.normal||c.card_faces?.[0]?.image_uris?.normal||'',scryfall_uri:c.scryfall_uri||'',lang:c.lang||'',digital:!!c.digital,foil:!!c.foil,nonfoil:!!c.nonfoil,prices:c.prices||null}}
function req(r){return new Promise((resolve,reject)=>{r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)})}
async function meta(key){let db=await open();return req(db.transaction(META,'readonly').objectStore(META).get(key))}
async function putMeta(k,v){let db=await open();return new Promise((resolve,reject)=>{let tx=db.transaction(META,'readwrite');tx.objectStore(META).put(v,k);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)})}
async function active(){return await meta('active')||STORE_A}
async function count(){let db=await open(),s=await active();return req(db.transaction(s,'readonly').objectStore(s).count())}
async function get(name){let native=nativeIndex('name',norm(name),1);if(native?.length)return native[0];let db=await open(),s=await active(),r=await req(db.transaction(s,'readonly').objectStore(s).index('nameKey').get(norm(name)));return r||null}
async function printings(name,limit=150){let db=await open(),s=await active();return new Promise((resolve,reject)=>{let out=[],tx=db.transaction(s,'readonly'),r=tx.objectStore(s).index('nameKey').openCursor(IDBKeyRange.only(norm(name)));r.onsuccess=()=>{let c=r.result;if(!c||out.length>=limit){resolve(out);return}out.push(c.value);c.continue()};r.onerror=()=>reject(r.error)})}
async function byId(id){let native=nativeIndex('id',String(id||''),1);if(native?.length)return native[0];let db=await open(),s=await active();return req(db.transaction(s,'readonly').objectStore(s).get(String(id||'')))}
async function bySetCollector(set,number){
 // A set/collector combination can represent multiple finishes or variants.
 // Never assume the first IndexedDB row is the only physical printing.
 const rows=await bySetCollectorAll(set,number,2);return rows.length===1?rows[0]:null;
}
async function indexedRows(index,key,limit=2001){
 const db=await open(),s=await active();return new Promise((resolve,reject)=>{
  const rows=[],tx=db.transaction(s,'readonly');
  const cursor=tx.objectStore(s).index(index).openCursor(IDBKeyRange.only(key));
  cursor.onsuccess=()=>{const c=cursor.result;if(!c||rows.length>=limit){resolve(rows);return}rows.push(c.value);c.continue()};
  cursor.onerror=()=>reject(cursor.error);
 });
}
async function bySetCollectorAll(set,number,limit=2001){
 const raw=String(number||'').toLowerCase();const key=raw.match(/^([a-z]?)(\d+)([a-z]?)$/);
 const normalized=key?key[1]+String(Number(key[2]))+key[3]:raw;
 const native=nativeIndex('setcollector',String(set).toLowerCase()+'#'+normalized,limit);
 if(native)return native;
 return indexedRows('setCollector',String(set).toLowerCase()+'#'+raw,limit);
}
async function byCollector(number,limit=2001){let native=nativeIndex('collector',String(number),limit);if(native)return native;return indexedRows('collectorNumber',String(number),limit)}
async function bySet(set,limit=2001){let native=nativeIndex('set',String(set).toLowerCase(),limit);if(native)return native;return indexedRows('setCode',String(set).toLowerCase(),limit)}
async function byIllustration(id,limit=2001){let native=nativeIndex('illustration',String(id),limit);if(native)return native;return indexedRows('illustration',String(id),limit)}
function nativeIndex(kind,key,max=2001){
 try{
  const bridge=window.ManaVaultNativeScanner;
  if(!bridge?.lookupOfflinePrintings)return null;
  const response=JSON.parse(bridge.lookupOfflinePrintings(kind,String(key),max));
  if(!response.ready||response.error||!response.complete||!Array.isArray(response.rows))return null;
  return response.rows;
 }catch{return null;}
}
async function fastPrintings(name,limit=2001){
 // Never wait for WebView IndexedDB metadata while the native read-only
 // catalog is available. During an IndexedDB bulk import, even metadata
 // transactions can stall live verification for many seconds.
 const native=nativeIndex('name',norm(name),limit);
 if(native){
  return {rows:native,complete:true,source:'native SQLite'};
 }
 // The native bundle may be unavailable on older APKs. Use the last
 // *committed* IndexedDB catalog as fallback; an in-progress import is never
 // treated as a complete source of editions.
 const available=await count().catch(()=>0);
 if(available<MIN_PRINTINGS)return {rows:[],complete:false,source:'Catalog warming (native '+
    String(window.ManaVaultNativeScanner?.nativeCatalogStatus?.()||'unavailable')+')'};
 const rows=await printings(name,limit);
 return {rows,complete:rows.length<limit,source:'IndexedDB'};
}

function status(s){lastStatus=s;let el=document.getElementById('mvDbStatus');if(el)el.textContent=s;try{window.mvLogCamera?.('Card database: '+s)}catch{};if(/^(Ready:|Up to date:)/.test(s))try{window.dispatchEvent(new Event('manavault:catalog-ready'))}catch{}}
function clear(db,s){return new Promise((resolve,reject)=>{let tx=db.transaction(s,'readwrite');tx.objectStore(s).clear();tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)})}
function putBatch(db,s,rows){return new Promise((resolve,reject)=>{let tx=db.transaction(s,'readwrite'),st=tx.objectStore(s);for(let r of rows)st.put(r);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)})}
async function ingest(stream,db,s){const reader=stream.getReader(),decoder=new TextDecoder();let collecting=false,depth=0,inString=false,escaped=false,obj='',rows=[],processed=0;
 async function flush(){if(!rows.length)return;await putBatch(db,s,rows);processed+=rows.length;rows=[];if(processed%5000<400)status('Importing '+processed.toLocaleString()+' printings…')}
 async function feed(chunk){for(let i=0;i<chunk.length;i++){
  const ch=chunk[i];
  if(!collecting){if(ch==='{'){collecting=true;depth=1;inString=false;escaped=false;obj='{';}continue;}
  obj+=ch;
  if(inString){if(escaped)escaped=false;else if(ch==='\\')escaped=true;else if(ch==='"')inString=false;continue}
  if(ch==='"'){inString=true;continue}
  if(ch==='{')depth++;
  else if(ch==='}'){
    depth--;
    if(depth===0){const c=JSON.parse(obj);if(c.id&&c.name)rows.push(record(c));collecting=false;obj='';if(rows.length>=350)await flush()}
  }
 }}

 try{while(true){const {value,done}=await reader.read();if(done)break;await feed(decoder.decode(value,{stream:true}))}await feed(decoder.decode());if(collecting)throw Error('Incomplete JSON bulk download');await flush();return processed}finally{reader.releaseLock()}
}
async function sync(force=false){if(running)return running;
 if(!force && window.mvNativeScannerBusy){status('Printing catalog update deferred while scanning.');return;}
 if(!force && !maintenanceAllowed && window.ManaVaultNativeScanner?.nativeCatalogStatus?.().startsWith('Ready:')){status('Ready: native SQLite printing catalog; update scheduled for idle time.');return;}running=(async()=>{let button=document.getElementById('mvDbSync');if(button)button.disabled=true;try{let existing=await count();let checked=Number(await meta('checked')||0);if(!force&&existing>=MIN_PRINTINGS&&Date.now()-checked<DAY){status('Ready: '+existing.toLocaleString()+' printings offline; checked within 24 hours.');return}if(!navigator.onLine){status(existing?'Offline: '+existing.toLocaleString()+' cached printings':'Offline: download begins when connected');return}
 status(existing?'Checking Scryfall printing updates…':'Downloading complete Scryfall printing database…');
 const headers={'Accept':'application/json','User-Agent':'ManaVault/4.2'};
 async function fetchMeta(url){
   const r=await fetch(url,{cache:'no-store',headers});
   if(!r.ok)throw Error('Scryfall metadata HTTP '+r.status+' ('+new URL(url).pathname+')');
   const payload=await r.json();
   return payload;
 }
 function pickBulk(obj){
   const entries=[obj,...(Array.isArray(obj?.data)?obj.data:[]),...(Array.isArray(obj?.results)?obj.results:[])].filter(Boolean);
   return entries.find(x=>x.type==='default_cards'&&typeof (x.jsonl_download_uri||x.download_uri)==='string'&&/^https:\/\//.test(x.jsonl_download_uri||x.download_uri));
 }
 let m=pickBulk(await fetchMeta('https://api.scryfall.com/bulk-data/default_cards'));
 if(!m)m=pickBulk(await fetchMeta('https://api.scryfall.com/bulk-data'));
 if(!m){
   throw Error('Scryfall bulk metadata returned no usable download_uri. Check API response/network; existing offline catalog preserved.');
 }

 let stamp=m.updated_at||m.id,url=m.jsonl_download_uri||m.download_uri;
 if(!url)throw Error('Scryfall metadata has no download_uri after endpoint, listing, and retry. Check network filtering or API response; no database was replaced.');if(existing>=MIN_PRINTINGS&&stamp===await meta('source')){await putMeta('checked',Date.now());status('Up to date: '+existing.toLocaleString()+' printings.');return}
 // The APK already contains this Scryfall snapshot. Do not waste storage and
 // bandwidth duplicating all 100k+ rows into IndexedDB just to check updates.
 const nativeVersion=window.ManaVaultNativeScanner?.nativeCatalogVersion?.();
 if(existing<MIN_PRINTINGS && stamp && nativeVersion===stamp){
  await putMeta('checked',Date.now());
  status('Up to date: native SQLite catalog ('+nativeVersion+').');return;
 }
 if(!force && window.mvNativeScannerBusy){status('Update postponed; card scanning active.');return;}
 let db=await open(),target=(await active())===STORE_A?STORE_B:STORE_A;await clear(db,target);status('Downloading '+(m.size?Math.round(m.size/1048576)+' MB':'bulk file')+' of Scryfall printings…');let bulk=await fetch(url);if(!bulk.ok||!bulk.body)throw Error('Bulk download failed (HTTP '+bulk.status+')');let stream=bulk.body;if(/\.gz(?:\?|$)/.test(url)){if(!window.DecompressionStream)throw Error('Gzip unsupported');stream=stream.pipeThrough(new DecompressionStream('gzip'))}
 let n;
 if(m.jsonl_download_uri || /\.jsonl(?:\.gz)?(?:\?|$)/i.test(url)){
   const reader=stream.pipeThrough(new TextDecoderStream()).getReader();let buffer='',batch=[],processed=0;
   async function addLine(line){if(!line.trim())return;let card=JSON.parse(line);if(card.id&&card.name)batch.push(record(card));if(batch.length>=350){await putBatch(db,target,batch);processed+=batch.length;batch=[];if(processed%3500===0)status('Importing '+processed.toLocaleString()+' printings…')}}
   try{while(true){let {value,done}=await reader.read();if(done)break;buffer+=value;let i;while((i=buffer.indexOf('\n'))>=0){await addLine(buffer.slice(0,i));buffer=buffer.slice(i+1)}}if(buffer.trim())await addLine(buffer);if(batch.length){await putBatch(db,target,batch);processed+=batch.length}n=processed}finally{reader.releaseLock()}
 }else n=await ingest(stream,db,target);if(n<MIN_PRINTINGS)throw Error('Received only '+n.toLocaleString()+' printings; existing catalog retained');let stored=await req(db.transaction(target,'readonly').objectStore(target).count());if(stored<MIN_PRINTINGS)throw Error('Only '+stored+' unique printing IDs; existing catalog retained');await putMeta('active',target);await putMeta('source',stamp);await putMeta('checked',Date.now());status('Ready: '+stored.toLocaleString()+' printings offline. Updated '+stamp+'.')
 }catch(e){let existing=await count().catch(()=>0);status('Sync failed: '+e.message+(existing?' — '+existing.toLocaleString()+' previous printings preserved':''))}finally{if(button)button.disabled=false}})();try{return await running}finally{running=null}}
async function init(){try{let n=await count();let nativeReady=window.ManaVaultNativeScanner?.nativeCatalogStatus?.().startsWith('Ready:');
 status(n?'Ready: '+n.toLocaleString()+' cached printings. Checking updates…':nativeReady?'Native printing catalog ready; WebView cache will update while idle.':'No printings yet; automatic download starting…');
 if(n<MIN_PRINTINGS && window.ManaVaultNativeScanner?.lookupOfflinePrintings){
  // APK contains its own catalog. It may still be decompressing on first use.
  // Surface the actual state instead of reporting that a bulk download started.
  let polls=0;
  const awaitNative=()=>{
   const current=window.ManaVaultNativeScanner?.nativeCatalogStatus?.()||'Native catalog unavailable';
   if(current.startsWith('Ready:')){
    status(current+'; ready for offline edition matching.');
    window.dispatchEvent(new Event('manavault:catalog-ready'));
   }else if(current.startsWith('IndexedDB fallback:')){
    status('Native catalog failed: '+current+'; online backup required.');
    if(!window.mvNativeScannerBusy)void sync();
   }else if(++polls<120){
    if(polls%5===1)status('Preparing packaged offline catalog… ('+polls+'s)');
    setTimeout(awaitNative,1000);
   }else status('Native catalog has not initialized; recognized names can be saved without guessed editions.');
  };
  awaitNative();
  setTimeout(()=>{maintenanceAllowed=true;if(!window.mvNativeScannerBusy)void sync();},120000);
 }else void sync();
 }catch(e){status('Database unavailable: '+e.message)}}
window.ManaVaultCardDB={get,byId,count,printings,fastPrintings,bySetCollector,bySetCollectorAll,byCollector,bySet,byIllustration,nativeIndex,sync,init,status:()=>lastStatus};window.addEventListener('online',()=>void sync());
})();
