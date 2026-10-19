/* ManaVault v5.12.8 — independent, catalog-powered Commander deck creation.
   A generated deck is a plan, never a claim that physical cards are owned.
   All remote requests run only when a user asks to search/build. */
(function(){
'use strict';
const api=window.ManaVaultDeckIntelligence;
if(!api)return;
const COLORS=['W','U','B','R','G'];
const BASICS={W:'Plains',U:'Island',B:'Swamp',R:'Mountain',G:'Forest'};
let matches=[], chosen=null, requestSerial=0, building=false;
const key=s=>String(s||'').trim().toLowerCase();
const isLand=c=>/\bland\b/i.test(c.type_line||'');
const basic=c=>/^basic land\b/i.test(c.type_line||'');
const row=(c,reason,qty=1)=>({id:c.id||'',name:c.name,qty,section:'main',reason:reason||'',role:api.role(c),set:c.set||'',collector_number:c.collector_number||''});
function commanderEligible(c){
 const rules=String(c.oracle_text||c.card_faces?.map(f=>f.oracle_text||'').join('\n')||'');
 return !!(c.name && (/(?:legendary creature)/i.test(c.type_line||'') || /can be your commander/i.test(rules) || c.is_commander===true)
   && c.legalities?.commander!=='banned' && c.legalities?.commander!=='not_legal');
}
function unique(cards){const seen=new Map();for(const c of cards){if(!c||!c.name)continue;
 const k=key(c.name);let old=seen.get(k);
 if(!old||(String(c.oracle_text||'').length>String(old.oracle_text||'').length))seen.set(k,c);
}return [...seen.values()];}
function makeCatalogDeck(cmd,catalog){
 const colors=(cmd.color_identity||[]).filter(x=>COLORS.includes(x));
 const legal=unique(catalog).filter(c=>key(c.name)!==key(cmd.name) && api.legal(c,colors)
  && c.legalities?.commander!=='banned' && c.legalities?.commander!=='not_legal');
 const scored=legal.filter(c=>!isLand(c)).map(c=>({card:c,...api.gradeCard(c,api.commanderProfile(cmd))})).filter(x=>x.score>=0);
 scored.sort((a,b)=>b.score-a.score||String(a.card.name).localeCompare(String(b.card.name)));
 const picked=[],names=new Set();let slots=63;
 const add=x=>{const k=key(x.card.name);if(!slots||names.has(k))return false;
  names.add(k);picked.push(row(x.card,x.reasons.join(' · ')||'Color-legal Commander support'));slots--;return true;};
 // Fill a functional core before the pure synergy pool. Roles may overlap, but each card appears once.
 for(const [role,target] of [['ramp',10],['draw',10],['interaction',10]]){
  let count=0;for(const x of scored){if(count>=target||!slots)break;if(x.role===role&&add(x))count++;}
 }
 for(const x of scored){if(!slots)break;add(x);}
 const requestedNonlands=picked.length;
 // Aim for 36 lands. Up to 12 color-legal nonbasic lands; basics fill out the rest.
 let lands=legal.filter(c=>isLand(c)&&!basic(c)).map(c=>({...api.gradeCard(c,api.commanderProfile(cmd)),card:c}));
 lands=lands.filter(x=>x.score>=0).sort((a,b)=>b.score-a.score||String(a.card.name).localeCompare(String(b.card.name)));
 for(const x of lands.slice(0,12))picked.push(row(x.card,'Mana base'));
 const landCount=36,needed=landCount-Math.min(12,lands.length);
 const active=colors.length?colors:['C'];
 const weights={};for(const color of active)weights[color]=2;
 for(const r of picked){const origin=legal.find(c=>key(c.name)===key(r.name));if(!origin||isLand(origin))continue;
  for(const color of origin.color_identity||[]){if(weights[color]!=null)weights[color]++;}
 }
 const totalWeight=Object.values(weights).reduce((a,b)=>a+b,0);
 const allocation=active.map(color=>({color,desired:needed*weights[color]/totalWeight,qty:Math.floor(needed*weights[color]/totalWeight)}));
 let remainder=needed-allocation.reduce((sum,x)=>sum+x.qty,0);
 for(const x of allocation.slice().sort((a,b)=>(b.desired-b.qty)-(a.desired-a.qty))){if(remainder-->0)x.qty++;}
 for(const x of allocation){if(!x.qty)continue;const name=BASICS[x.color]||'Wastes';picked.push({name,qty:x.qty,section:'main',reason:'Basic mana source',role:'land'});}
 return {cards:picked,profile:api.commanderProfile(cmd),count:1+picked.reduce((n,c)=>n+c.qty,0),missingNonlands:63-requestedNonlands,
  warning:requestedNonlands<63?'Catalog search supplied only '+requestedNonlands+' of 63 nonland cards. Add more cards in the editor before playing.':''};
}
function searchQueries(cmd){
 const c=(cmd.color_identity||[]).filter(x=>COLORS.includes(x));const color=c.length?'id<='+COLORS.filter(x=>c.includes(x)).join('').toLowerCase():'id=c';
 const p=api.commanderProfile(cmd),themes=p.themes.map(x=>x.id);
 const q=[`${color} f:commander -t:land`,`${color} f:commander otag:ramp`,`${color} f:commander otag:draw`,`${color} f:commander otag:removal`,`${color} f:commander t:land -t:basic`];
 if(p.tribe)q.push(`${color} f:commander t:${p.tribe.toLowerCase()}`);
 const specialized={artifacts:'t:artifact',energy:'o:"energy"',vehicles:'t:vehicle',spells:'(t:instant or t:sorcery)',tokens:'o:"create"',counters:'o:"+1/+1 counter"',sacrifice:'o:"sacrifice"',graveyard:'o:"graveyard"',blink:'o:"exile"',treasure:'o:"treasure"',landfall:'o:"landfall"',enchantments:'t:enchantment',equipment:'t:equipment',lifegain:'o:"gain life"',combat:'o:"combat"'};
 for(const t of themes.slice(0,3))if(specialized[t])q.push(`${color} f:commander ${specialized[t]}`);
 return [...new Set(q)];
}
async function fetchPool(cmd,lookup,progress){
 const queries=searchQueries(cmd),all=[];
 // Limit pages and total requests; catalog suggestions must remain responsive.
 for(let i=0;i<queries.length;i++){
  progress?.(`Searching catalog ${i+1} of ${queries.length}…`);
  const pages=(i===0)?3:1;
  for(let page=1;page<=pages;page++){
   let data;try{data=await lookup('/cards/search?q='+encodeURIComponent(queries[i])+'&unique=cards&order=edhrec&page='+page);}catch(e){if(i===0&&page===1)throw e;break;}
   if(!Array.isArray(data?.data))break;
   all.push(...data.data);if(!data.has_more)break;
  }
 }
 return unique(all);
}
function cardImg(c){return c.image_uris?.small||c.card_faces?.[0]?.image_uris?.small||c.image||'';}
function creatorPanel(){return `<section class="panel mv-create-panel" id="mvCommanderCreator"><div class="mv-strategy-heading"><div><p class="mv-eyebrow">Design without owning the cards</p><h2>Create from any Commander</h2><p class="muted small">Choose a legal commander from the catalog, then generate a suggested 100-card deck or start an empty deck. Nothing is added to your physical collection.</p></div></div>
 <label for="mvCommanderFind">Commander name</label><div class="flex mv-creator-search"><input id="mvCommanderFind" class="grow" placeholder="Search commanders (e.g. Shorikai, Wick, Huatli)" autocomplete="off"><button id="mvCommanderFindBtn" type="button" class="primary">Find</button></div>
 <div id="mvCommanderHits" class="mv-commander-results" aria-live="polite"></div><div id="mvCommanderSelection"></div>
 <label for="mvBuildMode">Starting point</label><select id="mvBuildMode"><option value="catalog">Generate from all legal catalog cards</option><option value="owned">Generate using owned cards only</option><option value="blank">Start blank and choose every card myself</option></select>
 <div class="flex"><button id="mvCreateCommanderDeck" type="button" class="primary" disabled>Create deck</button><span id="mvCommanderCreateStatus" class="small muted" role="status">Select a commander first.</span></div>
 <p class="muted small">Catalog generation uses Scryfall online to find recommendations; afterward the deck is saved locally and freely editable. It prioritizes commander-text synergy, color identity, roles and mana sources. Review the resulting list for format legality and power level.</p></section>`;}
function showMatches(){const el=document.getElementById('mvCommanderHits');if(!el)return;
 el.innerHTML=matches.length?matches.slice(0,15).map((c,i)=>`<button class="mv-commander-hit" type="button" data-mv-pick-commander="${i}"><img loading="lazy" src="${esc(cardImg(c)||mv594Placeholder)}" alt=""><span><b>${esc(c.name)}</b><span class="small muted">${esc(c.type_line||'')} · ${esc((c.color_identity||[]).join('')||'Colorless')}</span></span></button>`).join(''):'<p class="muted small">No eligible commanders found.</p>';
 el.querySelectorAll('[data-mv-pick-commander]').forEach(b=>b.onclick=()=>selectCommander(matches[Number(b.dataset.mvPickCommander)]));
}
function selectCommander(c){if(!c)return;chosen=c;
 const el=document.getElementById('mvCommanderSelection');if(el){const p=api.commanderProfile(c);
  el.innerHTML=`<div class="mv-commander-selected"><img src="${esc(cardImg(c)||mv594Placeholder)}" alt=""><div><b>${esc(c.name)}</b><div class="small muted">${esc(p.themes.map(x=>x.name).slice(0,5).join(' · ')||'General Commander')} · ${esc((c.color_identity||[]).join('')||'Colorless')}</div><p class="small muted">${esc(p.text.slice(0,350)||'Rules text unavailable')}</p></div></div>`;}
 const btn=document.getElementById('mvCreateCommanderDeck');if(btn)btn.disabled=false;
 const status=document.getElementById('mvCommanderCreateStatus');if(status)status.textContent='Ready to create.';
}
async function findCommander(){const input=document.getElementById('mvCommanderFind'),target=document.getElementById('mvCommanderHits');
 const q=String(input?.value||'').trim();if(!target)return;
 if(q.length<2){target.textContent='Enter at least two characters.';return;}
 const serial=++requestSerial;target.textContent='Searching commanders…';matches=[];chosen=null;
 const b=document.getElementById('mvCreateCommanderDeck');if(b)b.disabled=true;
 try{const result=await scry('/cards/search?q='+encodeURIComponent('is:commander '+q)+'&unique=cards&order=name');
  if(serial!==requestSerial)return;matches=(result.data||[]).filter(commanderEligible);showMatches();
 }catch(e){if(serial!==requestSerial)return;
  try{const card=await scry('/cards/named?fuzzy='+encodeURIComponent(q));if(serial!==requestSerial)return;matches=commanderEligible(card)?[card]:[];showMatches();}
  catch(err){target.textContent='Search unavailable. Check your connection or try a different commander name.';}}
}
async function createCommanderDeck(){if(building||!chosen)return;const cmd=chosen,mode=document.getElementById('mvBuildMode')?.value||'catalog';
 const status=document.getElementById('mvCommanderCreateStatus'),btn=document.getElementById('mvCreateCommanderDeck');
 building=true;if(btn)btn.disabled=true;
 const progress=s=>{if(status)status.textContent=s;};
 try{
  let build={cards:[],profile:api.commanderProfile(cmd),count:1,warning:''};
  if(mode==='owned'){
   build=api.createConcept(cmd,state.cards,c=>typeof physicalAvailable==='function'?physicalAvailable(key(c.name),-1):(c.qty||0));
   build.count=1+build.count;
   build.warning=build.count!==100?'Collection-only starter: '+build.count+' of 100 cards. Add missing cards in the editor.':'';
  }else if(mode==='catalog'){
   const available=await fetchPool(cmd,scry,progress);
   if(!available.length)throw Error('No catalog cards returned. Try again when online.');
   build=makeCatalogDeck(cmd,available);
  }
  const today=new Date().toLocaleDateString(),entry={name:cmd.name+(mode==='blank'?' — new deck':' — synergy deck'),commander:cmd.name,
   colors:cmd.color_identity||[],cards:build.cards,created:today,
   strategy:build.profile.themes.map(x=>x.name),source:mode==='catalog'?'scryfall-catalog':mode==='owned'?'collection':'manual',
   description:build.warning||'Suggested cards are editable. Catalog suggestions do not count as owned.'};
  state.commanderReferences=state.commanderReferences||{};
  state.commanderReferences[key(cmd.name)]={...info(cmd),oracle_text:cmd.oracle_text||cmd.card_faces?.map(f=>f.oracle_text||'').join('\n')||'',type_line:cmd.type_line||cmd.card_faces?.[0]?.type_line||'',color_identity:cmd.color_identity||[]};
  state.decks.push(entry);
  const i=state.decks.length-1;
  save();openDeck(i);
  if(build.warning){const p=document.getElementById('deckDetail');if(p){let note=document.createElement('div');note.className='notice';note.textContent=build.warning;p.prepend(note);}}
 }catch(e){progress('Could not create deck: '+String(e.message||e));}
 finally{building=false;if(btn && document.contains(btn))btn.disabled=!chosen;}
}
const previousDecks=decks,previousBind=bind,previousOpenDeck=openDeck;
let deckSearchResults=[];
function attachCatalogAdd(i){
 const holder=document.getElementById('deckDetail');if(!holder)return;
 const panel=document.createElement('section');panel.className='mv-strategy mv-deck-add-search';
 panel.innerHTML=`<h3>Add any legal card</h3><p class="small muted">Search the entire Scryfall catalog. Suggestions do not claim ownership. Your deck editor remains freely editable.</p><div class="flex"><input id="mvDeckCardQuery" class="grow" placeholder="Search by card name"><button type="button" id="mvDeckCardFind">Find</button></div><div id="mvDeckCardResults" aria-live="polite"></div>`;
 holder.appendChild(panel);
 const query=panel.querySelector('#mvDeckCardQuery'),results=panel.querySelector('#mvDeckCardResults');
 const find=async()=>{const term=query.value.trim();if(term.length<2){results.textContent='Enter at least two characters.';return;}
  results.textContent='Searching cards…';
  try{const r=await scry('/cards/search?q='+encodeURIComponent(term)+'&unique=cards&order=name');
   const deck=state.decks[i];if(!deck)return;
   const colors=deck.colors||state.commanderReferences?.[key(deck.commander)]?.color_identity||[];
   deckSearchResults=unique((r.data||[]).filter(c=>api.legal(c,colors) && key(c.name)!==key(deck.commander))).slice(0,20);
   results.innerHTML=deckSearchResults.length?deckSearchResults.map((c,index)=>`<div class="mv-deck-add-result"><img loading="lazy" src="${esc(cardImg(c)||mv594Placeholder)}" alt=""><div><b>${esc(c.name)}</b><div class="small muted">${esc(c.type_line||'')}</div></div><button type="button" data-mv-deck-add="${index}">Add</button></div>`).join(''):'<p class="muted small">No Commander-legal matching cards.</p>';
   results.querySelectorAll('[data-mv-deck-add]').forEach(b=>b.onclick=()=>{
    const c=deckSearchResults[Number(b.dataset.mvDeckAdd)];if(!c)return;
    const edit=document.getElementById('editDeckText'),saveEdit=document.getElementById('saveDeckEdit');if(!edit||!saveEdit)return;
    const lines=edit.value.split(/\r?\n/),limit=/(?:^basic land\b|a deck can have any number of cards named|a deck can have up to)/i.test((c.type_line||'')+' '+(c.oracle_text||''));
    if(!limit&&lines.some(line=>key(line.replace(/^\s*\d+\s*x?\s+/i,''))===key(c.name))){results.textContent='That card is already in this deck. Commander is normally singleton.';return;}
    edit.value+=(edit.value.trim()?'\n':'')+'1 '+c.name;
    saveEdit.click(); // routes through the existing deck editor and never changes collection ownership.
   });
  }catch(err){results.textContent='Catalog search unavailable: '+String(err.message||err);}
 };
 panel.querySelector('#mvDeckCardFind').onclick=find;
 query.onkeydown=e=>{if(e.key==='Enter')find();};
}
openDeck=function(i){previousOpenDeck(i);attachCatalogAdd(i);};

decks=function(){const html=previousDecks();return html.replace('<div class="grid2"',creatorPanel()+'<div class="grid2"');};
bind=function(){previousBind();if(tab!=='Deck builder')return;
 const searchButton=document.getElementById('mvCommanderFindBtn');if(searchButton)searchButton.onclick=findCommander;
 const input=document.getElementById('mvCommanderFind');if(input)input.onkeydown=e=>{if(e.key==='Enter')findCommander();};
 const create=document.getElementById('mvCreateCommanderDeck');if(create)create.onclick=createCommanderDeck;
 // Deliberately do not auto-restore an earlier commander selection after page changes.
};
window.ManaVaultCommanderCreator={commanderEligible,makeCatalogDeck,searchQueries,fetchPool,unique};
})();
