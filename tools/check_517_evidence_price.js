/** ManaVault v5.12.18: phone-log printing ambiguities and exact-price provenance. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv61CollectorKey('),end=html.indexOf('function mv61NormalImage(',start);
assert(start>=0&&end>start);
const ctx={};vm.createContext(ctx);vm.runInContext(html.slice(start,end),ctx);
const card=(set,num,rarity='rare',id=set+num)=>({id,name:'Abyssal Harvester',set,collector_number:num,rarity});
function resolver(lines,prints){ctx.lines=lines;ctx.prints=prints;return vm.runInContext('mv126ResolveStructuredFooter(lines,prints)',ctx);}
const row=(text,zone='footer-left',pass='color')=>({text,zone,pass});
let prints=[card('fdn','54'),card('fdn','2'),card('eld','54')];
let result=resolver([row('R 0054'),row('2','footer-deep'),row('FDN EN','footer-line')],prints);
assert.equal(result.printing?.id,'fdn54','complete collector number must outrank isolated digit from OCR');
result=resolver([row('R 0054'),row('C 0002'),row('FDN EN','footer-line')],prints);
assert.equal(result.printing,undefined,'two strongly supported candidate numbers require review');
result=resolver([row('914'),row('CLB EN','footer-line'),row('C 2021','footer-deep')],[card('clb','914','rare')]);
assert.equal(result.printing?.id,'clb914','copyright / unrelated C should not veto correct collector and set');
result=resolver([row('LO264'),row('DSK EN','footer-line')],[card('dsk','264','common'),card('dsk','23','common')]);
assert.equal(result.printing?.id,'dsk264','OCR LO264 must recover printed collector 0264 only for known printing');
result=resolver([row('C 0914'),row('CLB EN','footer-line')],[card('clb','914','rare')]);
assert.equal(result.printing,undefined,'genuine conflicting rarity attached to collector must veto');
result=resolver([row('0054/261 R'),row('FDN EN','footer-line')],[card('fdn','54','common')]);
assert.equal(result.printing,undefined,'rarity following a collector fraction can contradict catalog');
result=resolver([row('R 0054'),row('FDN EN','footer-line')],[card('fdn','54'),card('fdn','54','rare','variant')]);
assert.equal(result.printing,undefined,'multiple variants sharing same set and number remain ambiguous');

const from=html.indexOf('// v5.12.18: Real prices are looked up by exact Scryfall printing ID');
const to=html.indexOf('// v5.12.11: Combined, offline collection-search filters.',from);
assert(from>=0&&to>from);
const uuid='d6b4fd44-1234-4acd-a1ac-a1b2c3d4e5f6';
let fetched=[];let saved=[];
const savedCard={id:uuid,name:'Arbor Elf',set:'wwk',collector_number:'95',price:null,qty:1,foil:false};
const priceCtx={window:{},navigator:{onLine:true},document:{getElementById:()=>null},
 state:{cards:[savedCard]},STORE:'manavault-v1',localStorage:{setItem:(...args)=>saved.push(args)},
 fetch:async(_url,init)=>{fetched.push(JSON.parse(init.body).identifiers);return {ok:true,json:async()=>({data:[{id:uuid,name:'Arbor Elf',set:'wwk',collector_number:'95',prices:{usd:'0.49',usd_foil:'1.50'}}]})}},
 mv53Number:v=>v===null?null:Number(v),mv53PrintingPrice:c=>Number(c.prices?.usd||0),
 mv61CollectorKey:v=>String(Number(v)),mv60CanonicalMatches:(a,b)=>a===b,
 mv60SavedHistory:[],render:()=>{throw Error('quiet refresh should not render')},setTimeout:()=>1};
vm.createContext(priceCtx);vm.runInContext(html.slice(from,to),priceCtx);
(async()=>{
 const result=await vm.runInContext(`mv517RefreshExactPrices(['${uuid}'],{quiet:true})`,priceCtx);
 assert.equal(result.updated,1);assert.equal(savedCard.price,0.49);assert.equal(savedCard.priceSource,'Scryfall USD');
 assert.equal(fetched[0][0].id,uuid,'lookup by exact Scryfall printing ID');
 assert(saved.length>0,'price persisted with the rest of collection data');
 const bad=priceCtx.fetch;
 priceCtx.fetch=async()=>({ok:true,json:async()=>({data:[{id:uuid,name:'Arbor Elf',set:'2xm',collector_number:'95',prices:{usd:'44'}}]})});
 savedCard.price=null;
 const mismatch=await vm.runInContext(`mv517RefreshExactPrices(['${uuid}'],{quiet:true})`,priceCtx);
 assert.equal(mismatch.updated,0);assert.equal(savedCard.price,null,'different set must never change a saved printing price');
 // Correct exactly one of two owned copies without touching saved decks.
 const corrStart=html.indexOf('function mv517ApplyPrintingCorrection('),
       corrEnd=html.indexOf('async function mv517OpenPrintingCorrection(',corrStart);
 assert(corrStart>=0&&corrEnd>corrStart);
 const original={id:'old',name:'Arbor Elf',set:'m10',collector_number:'172',qty:2,condition:'LP',foil:false,price:2};
 const deck={name:'Built deck',cards:[{id:'old',name:'Arbor Elf',qty:1}]};
 const corrState={cards:[original],decks:[deck]};
 let persistCount=0;
 const correctionContext={state:corrState,STORE:'manavault-v1',
  info:p=>({...p,price:null}),mv53PrintingPrice:()=>.49,
  localStorage:{setItem:()=>persistCount++},
  mv60CanonicalMatches:(a,b)=>a===b};
 vm.createContext(correctionContext);
 vm.runInContext(html.slice(corrStart,corrEnd),correctionContext);
 const corrected=vm.runInContext(`mv517ApplyPrintingCorrection('old',{id:'wwk-95',name:'Arbor Elf',set:'wwk',collector_number:'95'},1)`,correctionContext);
 assert.equal(corrected.qty,1);assert.equal(original.qty,1);
 assert.equal(corrState.cards.find(c=>c.id==='wwk-95').price,.49);
 assert.equal(corrState.cards.find(c=>c.id==='wwk-95').condition,'LP');
 assert.equal(corrState.decks[0].cards[0].id,'old','correcting inventory cannot silently edit an existing deck');
 assert.equal(persistCount,1,'correction must persist in a single write');
 assert.throws(()=>vm.runInContext(`mv517ApplyPrintingCorrection('old',{id:'off',name:'Different Card',set:'test',collector_number:'2'},1)`,correctionContext));
 assert(html.includes('Refresh exact-printing prices'));
 assert(html.includes('card.foil?p.usd_foil:p.usd'), 'normal prices must never silently fall back to foil-only prices');
 assert(html.includes('Scryfall USD, not live TCGplayer Market Price'));
 console.log('v5.12.18: credible collector ranking, rarity-veto boundaries, variant collisions and exact-printing Scryfall price refresh passed.');
})().catch(e=>{console.error(e);process.exit(1)});
