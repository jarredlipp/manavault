const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('app/src/main/assets/deck-intelligence.js','utf8');
const elements={};const state={cards:[],decks:[]};
const ctx={
 state, tab:'Dashboard', window:{}, document:{getElementById:id=>elements[id]||null,querySelectorAll:()=>[]},
 decks:()=>'<h1>My decks</h1>', openDeck:()=>{},buildDeck:()=>{},upgradeCandidates:()=>'',
 render:()=>{}, save:()=>{}, esc:x=>String(x).replace(/&/g,'&amp;'),image:c=>c.image||'',mv594Placeholder:'none',
 physicalAvailable:k=>state.cards.find(c=>c.name.toLowerCase()===k)?.qty||0,
 localStorage:{setItem:()=>{}},STORE:'test',scry:()=>{},info:x=>x,
};ctx.window=ctx.window;vm.createContext(ctx);vm.runInContext(source,ctx);
const api=ctx.window.ManaVaultDeckIntelligence;
function card(name,text,type='Creature',colors=[],qty=1,other={}){
 return {name,oracle_text:text,type_line:type,color_identity:colors,qty,id:name,legalities:{commander:'legal'},...other};
}
const saheeli=card('Saheeli, Radiant Creator','Whenever you cast an artifact spell, get {E}. Create a 1/1 colorless Construct artifact creature token.','Legendary Creature — Human Artificer',['U','R','G']);
const sol=card('Sol Ring','{T}: Add {C}{C}.','Artifact',[]);
const generator=card('Izzet Generatorium','Whenever you cast an artifact spell, you get {E}.','Artifact',['U','R']);
const goblin=card('Goblin Raider','A plain goblin.','Creature — Goblin',['R']);
const out=api.suggestions(saheeli,[sol,generator,goblin],c=>c.qty);
assert(out.profile.themes.some(x=>x.id==='artifacts'));
assert(out.profile.themes.some(x=>x.id==='energy'));
assert(out.items.find(x=>x.card.name==='Izzet Generatorium').score>out.items.find(x=>x.card.name==='Goblin Raider').score);
assert(out.items.find(x=>x.card.name==='Izzet Generatorium').reasons.includes('Energy'));
const wick=card('Wick, the Whorled Mind','Whenever one or more Rats you control deal combat damage to a player, draw a card. Sacrifice a Rat: each opponent loses life.','Legendary Creature — Rat Warlock',['U','B','R']);
const rat=card('Rat Colony','Rat Colony gets +1/+0 for each other Rat you control. A deck can have any number of cards named Rat Colony.','Creature — Rat',['B'],8);
const elf=card('Elvish Mystic','{T}: Add {G}.','Creature — Elf',['G']);
const rats=api.suggestions(wick,[rat,elf],c=>c.qty);
assert(rats.profile.tribe==='Rat');assert(rats.items.every(x=>x.card.name!=='Elvish Mystic'));
const build=api.createConcept(wick,[rat,sol],c=>c.qty);
assert.equal(build.cards.find(c=>c.name==='Rat Colony').qty,8);
const veyran=card('Veyran, Voice of Duality','Magecraft — Whenever you cast or copy an instant or sorcery spell, Veyran gets +1/+1.','Legendary Creature — Efreet Wizard',['U','R']);
const opt=card('Opt','Scry 1. Draw a card.','Instant',['U']);
const bear=card('Bear','Vanilla','Creature — Bear',['G']);
assert(api.suggestions(veyran,[opt,bear],c=>c.qty).items.some(x=>x.card.name==='Opt'));
assert(!api.legal(card('Forest','','Basic Land — Forest',[]),['U','R']));
assert(api.legal(card('Island','','Basic Land — Island',[]),['U','R']));
assert(!api.legal(card('Steam Vents','','Land — Island Mountain',[]),['B']));
assert(api.legal(card('Steam Vents','','Land — Island Mountain',[]),['U','R']));
assert(!api.legal(card('Goblin token','', 'Token Creature — Goblin',['R']),['R']));
const dwarves=card('Seven Dwarves','A deck can have up to seven cards named Seven Dwarves.','Creature — Dwarf',['R'],12);
assert.equal(api.createConcept(veyran,[dwarves],c=>c.qty).cards[0].qty,7);
const offcolor=card('Green card','Draw a card.','Instant',['G']);
assert(!api.suggestions(veyran,[offcolor],c=>c.qty).items.length);
console.log('v5.12.8 deck intelligence: commander-text archetypes, color identity, singleton exceptions, on-color lands, tokens and ownership passed.');
