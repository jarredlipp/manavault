/* ManaVault v5.12.8 — offline Commander rules-text synergy and focused UI enhancements.
   Works with collection metadata already stored by ManaVault; never guesses ownership. */
(function () {
 'use strict';
 const archetypes = [
  {id:'artifacts',name:'Artifacts',cmd:/artifact|construct|affinity|metalcraft|improvise|fabricate|historic spell/i,card:/artifact|construct|whenever you cast an artifact|artifact enters|artifact you control|metalcraft|improvise/i},
  {id:'energy',name:'Energy',cmd:/\{e\}|energy counter|pay \{e\}/i,card:/\{e\}|energy counter|energy you have/i},
  {id:'vehicles',name:'Vehicles',cmd:/vehicle|crew\s*\d|mount|saddle/i,card:/vehicle|crew\s*\d|pilot|becomes an artifact creature|untap target artifact/i},
  {id:'spells',name:'Spellslinger',cmd:/instant or sorcery|instant and sorcery|noncreature spell|copy (?:target|an?) (?:instant|sorcery|spell)|magecraft|prowess|storm/i,card:/instant|sorcery|magecraft|prowess|whenever you cast (?:an? )?(?:instant|sorcery|noncreature)|copy target|copy an? instant|copy an? sorcery|spell costs? \{?1\}? less/i},
  {id:'tokens',name:'Tokens',cmd:/creature token|token creature|tokens? you control|create (?:a|two|x|that many|one or more|an?|\d+)\b/i,card:/create (?:a|two|three|x|that many|one or more|an?|\d+) .* token|populate|tokens? you control|double .*tokens?/i},
  {id:'counters',name:'+1/+1 Counters',cmd:/\+1\/\+1 counter|modified creatures|proliferate|move (?:a|an?|any number of) counter/i,card:/\+1\/\+1 counter|proliferate|doubl(?:e|ing) .* counter|evolve|adapt\s*\d|bolster\s*\d/i},
  {id:'sacrifice',name:'Sacrifice',cmd:/sacrifice|creature dies|creatures? (?:you control )?die|dies,|blood artist/i,card:/sacrifice|whenever .* dies|whenever .* (?:leaves the battlefield|is put into a graveyard)|dies,|each opponent loses 1 life/i},
  {id:'graveyard',name:'Graveyard',cmd:/graveyard|mill(?:ed|ing)?|reanimat|return .*from your graveyard|escape|disturb|descend/i,card:/graveyard|mill\b|flashback|unearth|escape|return target creature card|recursion|disturb/i},
  {id:'blink',name:'Blink / ETB',cmd:/enters the battlefield|when(?:ever)? .* enters|exile .* return .* battlefield|flicker|leaves the battlefield/i,card:/enters the battlefield|when(?:ever)? .* enters|exile .* return .* battlefield|flicker/i},
  {id:'treasure',name:'Treasure & Mana',cmd:/treasure|mana value|mana from a treasure/i,card:/treasure|create .*token.*add \{|add .*mana|when(?:ever)? .* artifact enters/i},
  {id:'landfall',name:'Landfall',cmd:/landfall|land enters|play an additional land|whenever you play a land/i,card:/landfall|land enters|search your library for .*land|additional land|put .*land .*onto the battlefield/i},
  {id:'lifegain',name:'Lifegain',cmd:/gain life|gains life|whenever you gain life|life total/i,card:/gain life|gains life|lifelink|whenever you gain life/i},
  {id:'equipment',name:'Equipment / Voltron',cmd:/equipment|equipped|attach|aura attached|commander damage/i,card:/equipment|equip\s*\{|equip\s*\d|attached|enchanted creature|hexproof|double strike|indestructible/i},
  {id:'combat',name:'Combat',cmd:/whenever .* attacks|combat damage|attack with|attacking creatures|double strike/i,card:/haste|double strike|trample|first strike|whenever .* attacks|combat damage|additional combat/i},
  {id:'enchantments',name:'Enchantments',cmd:/enchantment|constellation|aura|enchantress/i,card:/enchantment|constellation|aura|enchant creature|whenever an enchantment/i}
 ];
 const supportedTribes = ['Sliver','Rat','Dinosaur','Elf','Goblin','Merfolk','Dragon','Angel','Demon','Zombie','Vampire','Human','Soldier','Wizard','Squirrel','Cat','Knight','Warrior','Rogue','Pirate','Elemental','Spirit','Phyrexian','Vehicle','Construct','Insect','Bird','Snake','Beast'];
 const clean = s => String(s||'').toLowerCase();
 function rules(c){return String(c?.oracle_text||c?.card_faces?.map(f=>f.oracle_text||'').join('\n')||'');}
 function nameKey(s){return clean(s).trim();}
 function commanderProfile(cmd){
  if(!cmd)return {themes:[],colors:[],text:'',tribe:null,missing:true};
  const text=rules(cmd), t=String(cmd.type_line||''), name=String(cmd.name||'');
  const themes=[];
  for(const a of archetypes){if(a.cmd.test(text))themes.push({...a,weight:3});}
  let tribe=null;
  for(const x of supportedTribes){
   if(new RegExp('\\b'+x+'s?\\b','i').test(text) ||
    (new RegExp('\\b'+x+'\\b','i').test(t) && /other .* creatures|creatures you control|creature spells|kindred|each .* creature|chosen type/i.test(text))){tribe=x;break;}
  }
  if(tribe)themes.unshift({id:'tribal',name:tribe+' kindred',tribe,weight:5});
  const words=text.toLowerCase();
  // Stronger emphasis for a card explicitly rewarding its primary action.
  for(const a of themes){if(a.id!=='tribal' && new RegExp('whenever|each time|if you|for each|you may', 'i').test(words))a.weight+=1;}
  return {themes,colors:Array.isArray(cmd.color_identity)?cmd.color_identity:[],text,tribe,missing:!text,commander:name};
 }
 function legal(c,colors){
  if(!c || c.legalities?.commander==='not_legal' || c.legalities?.commander==='banned')return false;
  if(!Array.isArray(c.color_identity))return false;
  if(/^(?:token|emblem|scheme|plane|phenomenon|vanguard)\b/i.test(c.type_line||'') || /^(?:token|emblem)$/i.test(c.layout||''))return false;
  if(/\bland\b/i.test(c.type_line||'')){
   const basic={Plains:'W',Island:'U',Swamp:'B',Mountain:'R',Forest:'G'};
   for(const [land,color] of Object.entries(basic)){
    if(new RegExp('\\b'+land+'\\b','i').test((c.type_line||'').split('—').slice(1).join('—')) && !colors.includes(color))return false;
   }
  }
  return c.color_identity.every(x=>colors.includes(x));
 }
 function role(c){
  const t=clean(c.type_line),o=clean(rules(c));
  if(/\bland\b/.test(t))return 'land';
  if(/destroy target|exile target|counter target spell|return target .* to .* hand|deals? .* damage to (?:any target|target creature)/.test(o))return 'interaction';
  if(/draw (?:a|one|two|three|x|that many|\d+|cards)|draws? a card|look at the top .* put .*hand/.test(o))return 'draw';
  if(/add \{|search your library for .*land|create .*treasure|costs? (?:\{1\}|1) less/.test(o))return 'ramp';
  if(/creature/.test(t))return 'creature';
  return 'support';
 }
 function gradeCard(c,profile){
  if(!legal(c,profile.colors))return {score:-1,reasons:[],role:null};
  const t=String(c.type_line||''),o=rules(c),all=(t+' '+o),lower=clean(all),roles=role(c);
  let score=0,reasons=[];
  for(const a of profile.themes){
   if(a.id==='tribal'){
    const subtype=t.split('—')[1]||'';
    if(new RegExp('\\b'+a.tribe+'\\b','i').test(subtype) || new RegExp('\\b'+a.tribe+'s?\\b','i').test(o)){
     score+=a.weight*3;reasons.push(a.name);
    }
   } else if(a.card.test(all)){
    score+=a.weight*2.5;reasons.push(a.name);
   }
  }
  // Theme-adjacent cards are useful, but should not outrank direct synergy.
  if(roles==='draw'){score+=3;reasons.push('Card advantage');}
  if(roles==='ramp'){score+=3;reasons.push('Mana acceleration');}
  if(roles==='interaction'){score+=3;reasons.push('Interaction');}
  if(roles==='land')score+=1;
  if(profile.themes.some(x=>x.id==='spells')&&/\binstant\b|\bsorcery\b/i.test(t))score+=2;
  if(profile.themes.some(x=>x.id==='vehicles')&&/\bvehicle\b/i.test(t))score+=3;
  if(profile.themes.some(x=>x.id==='artifacts')&&/\bartifact\b/i.test(t))score+=2;
  if(profile.themes.some(x=>x.id==='graveyard')&&/flashback|escape|unearth|disturb/i.test(o))score+=2;
  if(profile.themes.some(x=>x.id==='tokens')&&/double the number of|populate/i.test(o))score+=2;
  if(profile.themes.some(x=>x.id==='energy')&&/\{e\}/i.test(o))score+=3;
  const mv=Number(c.cmc ?? c.mana_value ?? 0);
  if(Number.isFinite(mv)&&mv>6 && roles!=='land')score-=Math.min(5,mv-6);
  return {score,reasons:[...new Set(reasons)].slice(0,3),role:roles};
 }
 function dedup(cards,commanderName){const found=new Map();for(const c of cards){
  if(!c || nameKey(c.name)===nameKey(commanderName))continue;
  const key=nameKey(c.name),prior=found.get(key);
  if(!prior || (rules(c).length>rules(prior).length))found.set(key,c);
 }return [...found.values()];}
 function suggestions(cmd,candidates,free){
  const profile=commanderProfile(cmd),colors=profile.colors;
  const arr=dedup(candidates,cmd?.name).filter(c=>(typeof free==='function'?free(c):Number(c.qty||0))>0)
   .map(c=>({card:c,...gradeCard(c,profile)})).filter(x=>x.score>=0)
   .sort((a,b)=>b.score-a.score||String(a.card.name).localeCompare(String(b.card.name)));
  return {profile,items:arr};
 }
 const basicLand = c=>/^basic land\b/i.test(c.type_line||'');
 function limitCopies(c){if(basicLand(c)||/a deck can have any number of cards named/i.test(rules(c)))return 99;
  if(/a deck can have up to seven cards named/i.test(rules(c)))return 7;
  if(/a deck can have up to nine cards named/i.test(rules(c)))return 9;
  return 1;}
 function createConcept(cmd,cards,free){
  const ranked=suggestions(cmd,cards,free),max=99,caps={land:36,creature:38,draw:13,ramp:12,interaction:13,support:20},counts={},chosen=[];
  // Favor theme cards first, then fill missing structural roles from the same ranked pool.
  const passes=[x=>x.reasons.some(r=>!['Card advantage','Mana acceleration','Interaction'].includes(r)),()=>true];
  const picked=new Set();let total=0;
  for(const pass of passes){for(const x of ranked.items){if(total>=max)break;
   if(!pass(x)||picked.has(nameKey(x.card.name)))continue;
   const r=x.role||'support',maxRole=caps[r]||20;
   if((counts[r]||0)>=maxRole)continue;
   const available=Math.max(0,Math.floor(Number(free(x.card))||0));
   const singleton=Math.min(limitCopies(x.card),available);
   const qty=Math.min(max-total,maxRole-(counts[r]||0),singleton);
   if(qty<=0)continue;
   chosen.push({id:x.card.id,name:x.card.name,qty,section:'main',reason:x.reasons.join(' · '),role:r});
   picked.add(nameKey(x.card.name));counts[r]=(counts[r]||0)+qty;total+=qty;
  }}
  return {cards:chosen,profile:ranked.profile,count:total};
 }
 function themeBadges(p){return p.themes.length?p.themes.slice(0,5).map(x=>'<span class="mv-theme">'+esc(x.name)+'</span>').join(''):'<span class="mv-theme muted">General Commander</span>';}
 function commanderCard(c){return c?`<div class="mv-commander-card"><img alt="Commander" loading="lazy" src="${esc(image(c)||mv594Placeholder)}"><div><div class="small muted">Commander strategy</div><h3>${esc(c.name)}</h3><div class="mv-theme-row">${themeBadges(commanderProfile(c))}</div><p class="small muted">${commanderProfile(c).missing?'Rules text missing from your collection record. Refresh commander details to improve suggestions.':esc(rules(c).slice(0,260))}</p></div></div>`:'';}
 function commandCardInCollection(name){return state.cards.find(c=>nameKey(c.name)===nameKey(name));}
 function recommendMarkup(d,i){const cmd=commandCardInCollection(d.commander);
  if(!cmd)return `<section class="mv-strategy"><h3>Commander synergy</h3><p class="muted small">Commander rules text is not in the collection yet. Add the commander or use Refresh commander details to get recommendations based on its abilities.</p><button type="button" data-mv-refresh="${i}">Refresh commander details</button></section>`;
  const demand=new Set((d.cards||[]).map(x=>nameKey(x.name)));
  const free=c=>typeof physicalAvailable==='function'?physicalAvailable(nameKey(c.name),i):(c.qty||0);
  const ranked=suggestions(cmd,state.cards,free).items.filter(x=>!demand.has(nameKey(x.card.name))).slice(0,12);
  const selected=(d.cards||[]).filter(x=>x.reason).slice(0,8);
  return `<section class="mv-strategy"><div class="mv-strategy-heading"><div><h3>Commander synergy</h3><p class="muted small">Ranked by the commander's printed abilities, color identity, and available collection copies.</p></div><button data-mv-refresh="${i}" type="button">Refresh rules text</button></div>${commanderCard(cmd)}${selected.length?`<div class="mv-selected-reasons"><h3>Why these cards were suggested</h3>${selected.map(x=>`<div class="mv-reason-row"><b>${esc(x.name)}</b><span class="small muted">${esc(x.reason)}</span></div>`).join('')}</div>`:''}<h3 style="margin-top:16px">Strong owned additions</h3><div class="mv-suggestion-list">${ranked.length?ranked.map(x=>`<div class="mv-suggestion"><img src="${esc(image(x.card)||mv594Placeholder)}" loading="lazy" alt=""><div><b>${esc(x.card.name)}</b><div class="small muted">${esc(x.reasons.join(' · ')||'On-color support')} · ${esc(x.role||'support')}</div></div><span class="mv-score">${Math.round(x.score)} pts</span></div>`).join(''):'<p class="muted small">No unreserved, on-color candidates in your collection. Add cards to see more suggestions.</p>'}</div></section>`;
 }
 // Reuse existing deck editor, allocation, import, and scan pathways unchanged.
 const originalDecks=decks,originalOpenDeck=openDeck;
 decks=function(){const html=originalDecks();return html.replace('<h1>My decks</h1>',`<div class="mv-page-heading"><div><p class="mv-eyebrow">Collection-powered</p><h1>Commander deck studio</h1><p class="muted">Find cards that work with what your commander actually does.</p></div><div class="mv-page-symbol">✦</div></div>`).replace('Add an existing deck','Import a decklist').replace('Saved decks (','Your decks (').replace('Build from cards you own','Discover by commander');};
 openDeck=function(i){originalOpenDeck(i);const d=state.decks[i],target=document.getElementById('deckDetail');if(!d||!target)return;
  // This UI is added after legacy deck editor and allocation markup; no inventory is mutated.
  let existing=target.querySelector('#mvCommanderInsights');if(existing)existing.remove();
  let section=document.createElement('div');section.id='mvCommanderInsights';section.innerHTML=recommendMarkup(d,i);target.insertBefore(section,target.firstChild);bindDetails();};
 const originalBuildDeck=buildDeck;
 buildDeck=function(id){const cmd=state.cards.find(c=>c.id===id);if(!cmd)return originalBuildDeck(id);
  const free=c=>typeof physicalAvailable==='function'?physicalAvailable(nameKey(c.name),-1):Number(c.qty||0);
  const build=createConcept(cmd,state.cards,free);
  state.decks.push({name:cmd.name+' — synergy starter',commander:cmd.name,colors:cmd.color_identity||[],cards:build.cards,created:new Date().toLocaleDateString(),strategy:build.profile.themes.map(t=>t.name),description:'Collection-only synergy concept; validate deck size, legality and mana base before playing.'});
  save();
 };
 // Replaces the previous generic upgrade candidate ranking in Insights.
 upgradeCandidates=function(i){const d=state.decks[i];if(!d)return '';const cmd=commandCardInCollection(d.commander);if(!cmd)return '<p class="muted small">Add the commander to your collection to analyze its printed abilities.</p>';
  const demand=new Set((d.cards||[]).map(x=>nameKey(x.name)));
  const free=c=>typeof physicalAvailable==='function'?physicalAvailable(nameKey(c.name),i):Number(c.qty||0);
  const arr=suggestions(cmd,state.cards,free).items.filter(x=>!demand.has(nameKey(x.card.name))).slice(0,12);
  return arr.length?arr.map(x=>`<div class="row"><span><b>${esc(x.card.name)}</b><div class="small muted">${esc(x.reasons.join(' · ')||'On-color support')} · ${free(x.card)} available</div></span><button data-copyupgrade="${esc(x.card.name)}">Copy</button></div>`).join(''):'<p class="muted small">No available, color-legal additions found.</p>';
 };
 function bindDetails(){document.querySelectorAll('[data-mv-refresh]').forEach(button=>button.onclick=async()=>{
  const i=+button.dataset.mvRefresh,d=state.decks[i];if(!d||!d.commander)return;
  button.disabled=true;button.textContent='Loading…';
  try{const card=await scry('/cards/named?exact='+encodeURIComponent(d.commander));
   const incoming=info(card);let existing=commandCardInCollection(d.commander);
   if(existing){Object.assign(existing,{oracle_text:incoming.oracle_text,type_line:incoming.type_line,color_identity:incoming.color_identity,cmc:incoming.cmc});}
   else { // Do NOT imply the commander is physically owned: keep only reference data separately.
    state.commanderReferences=state.commanderReferences||{};state.commanderReferences[nameKey(d.commander)]=incoming;
   }
   localStorage.setItem(STORE,JSON.stringify(state));openDeck(i);
  }catch(e){button.textContent='Unavailable — retry';button.title=String(e.message||e);button.disabled=false;}
 });}
 // Expose pure algorithms for regression tests, with no dependency on scanning.
 window.ManaVaultDeckIntelligence={commanderProfile,gradeCard,suggestions,createConcept,legal,role};
 // A saved deck can reference a commander without claiming physical ownership.
 const priorCommanderLookup=commandCardInCollection;
 commandCardInCollection=function(name){return priorCommanderLookup(name)||state.commanderReferences?.[nameKey(name)]||null;};
 if(tab==='Deck builder')render();
})();
