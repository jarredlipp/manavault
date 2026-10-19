const assert=require('assert'),fs=require('fs'),vm=require('vm');
const state={cards:[],decks:[],wishlist:[]};let lookupPaths=[];
const ctx={state,tab:'Dashboard',window:{},document:{getElementById:()=>null,querySelectorAll:()=>[],contains:()=>false},
 decks:()=>'<h1>My decks</h1><div class="grid2">old</div>',bind:()=>{},openDeck:()=>{},buildDeck:()=>{},upgradeCandidates:()=>{},
 render:()=>{},save:()=>{},esc:s=>String(s),image:()=>'',mv594Placeholder:'none',
 physicalAvailable:()=>0,localStorage:{setItem:()=>{}},STORE:'fake',scry:()=>{},info:c=>({...c}),
};vm.createContext(ctx);for(const file of ['deck-intelligence.js','commander-creator.js'])vm.runInContext(fs.readFileSync('app/src/main/assets/'+file,'utf8'),ctx);
const api=ctx.window.ManaVaultCommanderCreator;
assert(api);assert(ctx.decks().includes('Create from any Commander'));
const cmd={name:'Wick, the Whorled Mind',type_line:'Legendary Creature — Rat Warlock',
 oracle_text:'Whenever one or more Rats you control deal combat damage to a player, draw a card. Sacrifice a Rat: each opponent loses life.',
 color_identity:['U','B','R'],legalities:{commander:'legal'}};
const item=(name,type,ci=[],text='Draw a card.',extra={})=>({name,type_line:type,color_identity:ci,oracle_text:text,id:name,legalities:{commander:'legal'},...extra});
assert(api.commanderEligible(cmd));
assert(!api.commanderEligible(item('Not commander','Creature — Rat',['B'])));
let pool=[];
for(let i=0;i<85;i++)pool.push(item('Rat Candidate '+i,'Creature — Rat',['B'],'Whenever this Rat attacks, create a Rat token.'));
for(let i=0;i<15;i++)pool.push(item('Removal '+i,'Instant',['R'],'Destroy target creature.'));
for(let i=0;i<15;i++)pool.push(item('Mana Rock '+i,'Artifact',[],'{T}: Add {C}.'));
for(let i=0;i<15;i++)pool.push(item('Draw '+i,'Sorcery',['U'],'Draw two cards.'));
for(let i=0;i<16;i++)pool.push(item('Dual land '+i,'Land',[],'{T}: Add {B} or {R}.'));
pool.push(item('Offcolor forest','Land — Forest',['G'],'{T}: Add {G}.'));
pool.push(item('Forbidden Spell','Sorcery',['B'],'Draw three cards.',{legalities:{commander:'banned'}}));
pool.push(item('Wick, the Whorled Mind',cmd.type_line,cmd.color_identity,cmd.oracle_text));
let generated=api.makeCatalogDeck(cmd,pool);
assert.equal(generated.count,100,'generated deck + commander should be 100');
assert.equal(generated.cards.filter(c=>c.role==='land').reduce((n,c)=>n+c.qty,0),36,'mana base should be 36 lands');
assert(!generated.cards.some(c=>c.name==='Forbidden Spell'||c.name==='Offcolor forest'||c.name===cmd.name));
assert(!generated.cards.some(c=>c.name==='Forest'||c.name==='Plains'));
assert(generated.cards.some(c=>c.name==='Swamp'));
const names=new Set(generated.cards.filter(c=>!/(Plains|Island|Swamp|Mountain|Forest|Wastes)/.test(c.name)).map(c=>c.name));
assert.equal(names.size,generated.cards.filter(c=>!/(Plains|Island|Swamp|Mountain|Forest|Wastes)/.test(c.name)).length);
assert(generated.cards.every(c=>c.section==='main'&&c.qty>=1));
const noPool=api.makeCatalogDeck(cmd,[]);assert(noPool.count<100&&noPool.warning);
const colorless={name:'Karn',type_line:'Legendary Creature — Golem',oracle_text:'Artifact creatures get +1/+1.',color_identity:[],legalities:{commander:'legal'}};
const empty=api.makeCatalogDeck(colorless,[]);assert(empty.cards.some(c=>c.name==='Wastes'));
assert(api.searchQueries(cmd).some(q=>q.includes('t:rat')));
const singleColor={...cmd,color_identity:['B']};assert(api.searchQueries(singleColor)[0].includes('id<=b'));
(async()=>{
 const sample=pool.slice(0,4),queries=[];
 const result=await api.fetchPool(cmd,async path=>{queries.push(path);return {data:sample,has_more:false}},()=>{});
 assert.equal(result.length,4);assert(queries.length>=5);
 console.log('v5.12.8 any-Commander generator: full 100-card plan, mana base, color legality, ownership isolation, search, insufficient pool and reusable catalog data passed.');
})().catch(e=>{console.error(e);process.exitCode=1});
