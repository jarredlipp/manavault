/* ManaVault v4.0: Scryfall default_cards, all printings, automatic daily metadata checks.
   Two alternating IndexedDB stores protect the previous complete catalog if download fails.
   Records are keyed by Scryfall printing ID, NOT by normalized name. */
(function(){
'use strict';
const DB='manavault_printings_v40', META='meta', STORE_A='printings_a', STORE_B='printings_b';
const DAY=86400000, MIN_PRINTINGS=100000;
let opening, running=null, lastStatus='';
function open(){return opening ||= new Promise((resolve,reject)=>{const r=indexedDB.open(DB,1);r.onupgradeneeded=()=>{const db=r.result;for(const s of [STORE_A,STORE_B]){const st=db.createObjectStore(s,{keyPath:'id'});st.createIndex('nameKey','nameKey',{unique:false});st.createIndex('setCollector','setCollector',{unique:false});}db.createObjectStore(META)};r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)})}
function norm(s){return String(s||'').normalize('NFKD').toLowerCase().replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim()}
function record(c){return {id:c.id,nameKey:norm(c.name),name:c.name,oracle_id:c.oracle_id||'',set:c.set||'',set_name:c.set_name||'',collector_number:c.collector_number||'',setCollector:(c.set||'').toLowerCase()+'#'+String(c.collector_number||'').toLowerCase(),type_line:c.type_line||'',mana_cost:c.mana_cost||'',rarity:c.rarity||'',image_url:c.image_uris?.art_crop||c.card_faces?.[0]?.image_uris?.art_crop||'',image_small:c.image_uris?.small||c.card_faces?.[0]?.image_uris?.small||'',scryfall_uri:c.scryfall_uri||'',lang:c.lang||'',digital:!!c.digital,foil:!!c.foil,nonfoil:!!c.nonfoil}}
function req(r){return new Promise((resolve,reject)=>{r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)})}
async function meta(key){let db=await open();return req(db.transaction(META,'readonly').objectStore(META).get(key))}
async function putMeta(k,v){let db=await open();return new Promise((resolve,reject)=>{let tx=db.transaction(META,'readwrite');tx.objectStore(META).put(v,k);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)})}
async function active(){return await meta('active')||STORE_A}
async function count(){let db=await open(),s=await active();return req(db.transaction(s,'readonly').objectStore(s).count())}
async function get(name){let db=await open(),s=await active(),r=await req(db.transaction(s,'readonly').objectStore(s).index('nameKey').get(norm(name)));return r||null}
async function printings(name,limit=150){let db=await open(),s=await active();return new Promise((resolve,reject)=>{let out=[],tx=db.transaction(s,'readonly'),r=tx.objectStore(s).index('nameKey').openCursor(IDBKeyRange.only(norm(name)));r.onsuccess=()=>{let c=r.result;if(!c||out.length>=limit){resolve(out);return}out.push(c.value);c.continue()};r.onerror=()=>reject(r.error)})}
async function bySetCollector(set,number){let db=await open(),s=await active();return req(db.transaction(s,'readonly').objectStore(s).index('setCollector').get(String(set).toLowerCase()+'#'+String(number).toLowerCase()))}
function status(s){lastStatus=s;let el=document.getElementById('mvDbStatus');if(el)el.textContent=s;try{window.mvLogCamera?.('Card database: '+s)}catch{}}
function clear(db,s){return new Promise((resolve,reject)=>{let tx=db.transaction(s,'readwrite');tx.objectStore(s).clear();tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)})}
function putBatch(db,s,rows){return new Promise((resolve,reject)=>{let tx=db.transaction(s,'readwrite'),st=tx.objectStore(s);for(let r of rows)st.put(r);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)})}
async function ingest(stream,db,s){const reader=stream.getReader(),decoder=new TextDecoder();let collecting=false,depth=0,inString=false,escaped=false,obj='',rows=[],processed=0;
 async function flush(){if(!rows.length)return;await putBatch(db,s,rows);processed+=rows.length;rows=[];if(processed%5000<400)status('Importing '+processed.toLocaleString()+' printings…')}
 async function feed(chunk){for(let i=0;i<chunk.length;i++){const ch=chunk[i];if(!collecting){if(ch==='{'){collecting=true;depth=1;inString=false;escaped=false;obj='{'}continue}obj+=ch;if(inString){if(escaped)escaped=false;else if(ch==='\\')escaped=true;else if(ch==='"')inString=false;continue}if(ch==='"'){inString=true;continue}if(ch==='{')depth++;else if(ch==='}'){depth--;if(depth===0){const c=JSON.parse(obj);if(c.id&&c.name)rows.push(record(c));collecting=false;obj='';if(rows.length>=350)await flush()}}}}
 try{while(true){const {value,done}=await reader.read();if(done)break;await feed(decoder.decode(value,{stream:true}))}await feed(decoder.decode());if(collecting)throw Error('Incomplete JSON bulk download');await flush();return processed}finally{reader.releaseLock()}
}
async function sync(force=false){if(running)return running;running=(async()=>{let button=document.getElementById('mvDbSync');if(button)button.disabled=true;try{let existing=await count();let checked=Number(await meta('checked')||0);if(!force&&existing>=MIN_PRINTINGS&&Date.now()-checked<DAY){status('Ready: '+existing.toLocaleString()+' printings offline; checked within 24 hours.');return}if(!navigator.onLine){status(existing?'Offline: '+existing.toLocaleString()+' cached printings':'Offline: download begins when connected');return}
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
   return entries.find(x=>x.type==='default_cards'&&typeof x.download_uri==='string'&&/^https:\/\//.test(x.download_uri));
 }
 let m=pickBulk(await fetchMeta('https://api.scryfall.com/bulk-data/default_cards'));
 if(!m)m=pickBulk(await fetchMeta('https://api.scryfall.com/bulk-data'));
 if(!m){
   status('Scryfall bulk metadata incomplete; trying paginated card API (slower)…');
   const db=await open(),target=(await active())===STORE_A?STORE_B:STORE_A;
   await clear(db,target);
   let page=1,processed=0,next='https://api.scryfall.com/cards?order=name&unique=prints';
   while(next){
     const response=await fetch(next,{cache:'no-store',headers});
     if(!response.ok)throw Error('Scryfall card page '+page+' HTTP '+response.status);
     const result=await response.json();
     if(!Array.isArray(result.data))throw Error('Scryfall card API returned no records on page '+page);
     await putBatch(db,target,result.data.filter(c=>c.id&&c.name).map(record));
     processed+=result.data.length;
     if(page%10===0)status('Importing via Scryfall pages: '+processed.toLocaleString()+' records…');
     next=result.has_more?result.next_page:null;
     if(result.has_more&&!next)throw Error('Scryfall page '+page+' omitted next_page');
     page++;
     if(next)await new Promise(r=>setTimeout(r,120));
   }
   const stored=await req(db.transaction(target,'readonly').objectStore(target).count());
   if(stored<MIN_PRINTINGS)throw Error('Paginated import returned '+stored+' printings (minimum '+MIN_PRINTINGS+'); previous catalog retained');
   await putMeta('active',target);
   await putMeta('source','pages-'+new Date().toISOString().slice(0,10));
   await putMeta('checked',Date.now());
   status('Ready: '+stored.toLocaleString()+' printings offline (paginated Scryfall sync).');
   return;
 }
 let stamp=m.updated_at||m.id,url=m.download_uri;
 if(!url)throw Error('Scryfall metadata has no download_uri after endpoint, listing, and retry. Check network filtering or API response; no database was replaced.');if(!force&&existing>=MIN_PRINTINGS&&stamp===await meta('source')){await putMeta('checked',Date.now());status('Up to date: '+existing.toLocaleString()+' printings.');return}
 let db=await open(),target=(await active())===STORE_A?STORE_B:STORE_A;await clear(db,target);status('Downloading '+(m.size?Math.round(m.size/1048576)+' MB':'bulk file')+' of Scryfall printings…');let bulk=await fetch(url);if(!bulk.ok||!bulk.body)throw Error('Bulk download failed (HTTP '+bulk.status+')');let stream=bulk.body;if(/\.gz(?:\?|$)/.test(url)){if(!window.DecompressionStream)throw Error('Gzip unsupported');stream=stream.pipeThrough(new DecompressionStream('gzip'))}
 let n=await ingest(stream,db,target);if(n<MIN_PRINTINGS)throw Error('Received only '+n.toLocaleString()+' printings; existing catalog retained');let stored=await req(db.transaction(target,'readonly').objectStore(target).count());if(stored<MIN_PRINTINGS)throw Error('Only '+stored+' unique printing IDs; existing catalog retained');await putMeta('active',target);await putMeta('source',stamp);await putMeta('checked',Date.now());status('Ready: '+stored.toLocaleString()+' printings offline. Updated '+stamp+'.')
 }catch(e){let existing=await count().catch(()=>0);status('Sync failed: '+e.message+(existing?' — '+existing.toLocaleString()+' previous printings preserved':''))}finally{if(button)button.disabled=false}})();try{return await running}finally{running=null}}
async function init(){try{let n=await count();status(n?'Ready: '+n.toLocaleString()+' cached printings. Checking updates…':'No printings yet; automatic download starting…');void sync()}catch(e){status('Database unavailable: '+e.message)}}
window.ManaVaultCardDB={get,count,printings,bySetCollector,sync,init,status:()=>lastStatus};window.addEventListener('online',()=>void sync());
})();
