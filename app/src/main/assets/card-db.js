/* ManaVault v2.4 — local Scryfall Oracle card database. */
(function(){
'use strict';
const DB='manavault_scryfall_v24', STORE='cards';
let opening;
function open(){return opening ||= new Promise((resolve,reject)=>{let r=indexedDB.open(DB,1);r.onupgradeneeded=()=>{let db=r.result;let st=db.createObjectStore(STORE,{keyPath:'nameKey'});st.createIndex('id','id',{unique:false})};r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)})}
function norm(s){return String(s||'').normalize('NFKD').toLowerCase().replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim()}
function putBatch(db,records){return new Promise((resolve,reject)=>{let tx=db.transaction(STORE,'readwrite'),st=tx.objectStore(STORE);for(let x of records)st.put(x);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error)})}
function get(name){return open().then(db=>new Promise((resolve,reject)=>{let req=db.transaction(STORE,'readonly').objectStore(STORE).get(norm(name));req.onsuccess=()=>resolve(req.result||null);req.onerror=()=>reject(req.error)}))}
function count(){return open().then(db=>new Promise((resolve,reject)=>{let req=db.transaction(STORE,'readonly').objectStore(STORE).count();req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error)}))}
function setStatus(s){let el=document.getElementById('mvDbStatus');if(el)el.textContent=s;try{window.mvLogCamera?.('Card database: '+s)}catch{}}
function minimal(c){return {nameKey:norm(c.name),id:c.id||'',name:c.name,oracle_id:c.oracle_id||'',set:c.set||'',collector_number:c.collector_number||'',type_line:c.type_line||'',mana_cost:c.mana_cost||'',image_url:c.image_uris?.art_crop||c.card_faces?.[0]?.image_uris?.art_crop||'',scryfall_uri:c.scryfall_uri||''}}
async function sync(){let btn=document.getElementById('mvDbSync');if(btn)btn.disabled=true;try{
 setStatus('Connecting to Scryfall…');let metaResponse=await fetch('https://api.scryfall.com/bulk-data/oracle_cards');if(!metaResponse.ok)throw Error('Metadata HTTP '+metaResponse.status);let meta=await metaResponse.json();let url=meta.jsonl_download_uri||meta.download_uri;if(!url)throw Error('No bulk download URL');let res=await fetch(url);if(!res.ok)throw Error('Download HTTP '+res.status);
 let stream=res.body;if(!stream)throw Error('Streaming downloads unsupported');if(url.endsWith('.gz')){if(typeof DecompressionStream==='undefined')throw Error('Compressed database unsupported by this WebView');stream=stream.pipeThrough(new DecompressionStream('gzip'))}
 let reader=stream.pipeThrough(new TextDecoderStream()).getReader(),db=await open(),buffer='',batch=[],processed=0;
 async function consume(line){if(!line.trim())return;let card=JSON.parse(line);if(!card.name)return;batch.push(minimal(card));if(batch.length>=350){await putBatch(db,batch);processed+=batch.length;batch=[];if(processed%3500===0)setStatus('Importing cards: '+processed.toLocaleString())}}
 while(true){let {value,done}=await reader.read();if(done)break;buffer+=value;let end;while((end=buffer.indexOf('\n'))!==-1){let line=buffer.slice(0,end);buffer=buffer.slice(end+1);await consume(line)}}if(buffer.trim())await consume(buffer);if(batch.length){await putBatch(db,batch);processed+=batch.length}
 localStorage.setItem('mv_db_updated_v24',new Date().toISOString());setStatus('Ready: '+(await count()).toLocaleString()+' locally stored card records. Updated '+new Date().toLocaleDateString());
 }catch(e){setStatus('Sync failed: '+e.message+' — existing cached cards remain available')}finally{if(btn)btn.disabled=false}}
async function init(){try{let n=await count();setStatus(n?n.toLocaleString()+' card records stored offline. Last sync: '+(localStorage.getItem('mv_db_updated_v24')||'unknown'):'No local database yet. Tap Download card database (Wi-Fi recommended).')}catch(e){setStatus('Local database unavailable: '+e.message)}}
window.ManaVaultCardDB={get,count,sync,init};
})();