/** Regression tests for automatic printing selection. No network/camera required. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const start=html.indexOf('// v5.10: auto-save');
const end=html.indexOf('</script>',start);
assert.ok(start>0&&end>start,'Missing automatic scan implementation');
const code=html.slice(start,end);
const copies=[];
let localCards=[];
let imageResults=[];
let printedLines=[];
let printedToken=false;
let symbolResults=[];
const printings=[
 {id:'a',name:'Test Card',lang:'en',digital:false,nonfoil:true,foil:false,set:'one',set_name:'One Set',collector_number:'12',image_small:'https://cards.scryfall.io/small/front/a/b/a.jpg',image_url:'https://cards.scryfall.io/art1.jpg',illustration_id:'art-1'},
 {id:'b',name:'Test Card',lang:'en',digital:false,nonfoil:true,foil:false,set:'two',set_name:'Two Set',collector_number:'49',image_small:'https://cards.scryfall.io/small/front/c/d/b.jpg',image_url:'https://cards.scryfall.io/art2.jpg',illustration_id:'art-2'}
];
const context={
 window:{},console,
 setTimeout:()=>1,clearTimeout:()=>{},
 navigator:{onLine:false},
 ManaVaultCardDB:{count:async()=>118773,printings:async()=>localCards,bySetCollector:async(set,n)=>localCards.find(c=>c.set===set&&c.collector_number===n)||null},
 mvHybridNorm:x=>String(x||'').toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim(),
 mvHybridEdit:(a,b)=>{let p=Array.from({length:b.length+1},(_,i)=>i);for(let i=1;i<=a.length;i++){const q=[i];for(let j=1;j<=b.length;j++)q[j]=Math.min(q[j-1]+1,p[j]+1,p[j-1]+(a[i-1]===b[j-1]?0:1));p=q;}return p[b.length]},
 mvHybridLoadNames:async()=>{},
 mvHybridCandidates:s=>[{name:s,score:1,exact:true}],
 mvLogCamera:()=>{},
 state:{cards:[],decks:[]},scanAlsoOwn:true,
 insertIntoDeck:()=>false,
 info:c=>({id:c.id,name:c.name,image:c.image_small||'',price:null}),
 mv53PrintingPrice:()=>null,
 localStorage:{setItem:()=>{}},STORE:'test',
 mvCacheReferenceArtwork:async()=>{},scanTone:()=>{},
 render:()=>{},tab:'Scan cards',scanner:()=>'',bind:()=>{},save:()=>{},scanDeckIndex:-1,
 document:{getElementById:()=>null},
 mv58NativeStatus:()=>{},
 mv59Next:()=>{},
 mv59Queue:[],mv59Cursor:-1,
 scry:async()=>{throw Error('Network not needed in offline test')},
};
context.window.ManaVaultCardDB=context.ManaVaultCardDB;
context.window.ManaVaultNativeScanner={
 selectImage:()=>{},
 readPrintingMarks:request=>{
  const payload=JSON.parse(request);
  context.window.mv61OnMarksResult({requestId:payload.requestId,lines:printedLines,token:printedToken});
 },
 rankSetSymbols:request=>{
  const payload=JSON.parse(request);
  context.window.mv61OnSymbolResult({requestId:payload.requestId,matches:symbolResults,attempted:payload.choices.length,failed:0});
 },
 rankPrintings:request=>{
  const payload=JSON.parse(request);
  context.window.mv60OnRankResult({requestId:payload.requestId,matches:imageResults,attempted:payload.choices.length,failed:0});
 }
};
context.ManaVaultNativeScanner=context.window.ManaVaultNativeScanner;
vm.createContext(context);vm.runInContext(code,context,{filename:'auto-save.js'});
async function run(expr){return vm.runInContext(expr,context)}
(async()=>{
 assert.equal(await run('mv60CanonicalMatches("Tuinvale Treefolk // Oaken Boon","Oaken Boon")'),true);
 localCards=[printings[0]];
 let result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},0)');
 assert.equal(result.printing?.id,'a','Exactly one physical printing should auto-save');
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr",ocr_reads:1},0)');
 assert.equal(!!result.printing,false,'One unsupported fuzzy OCR read cannot auto-save');
 localCards=printings;
 imageResults=[{id:'b',score:.976},{id:'a',score:.824}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.equal(result.printing?.id,'b','Distinct artwork winner should select printing');
 imageResults=[{id:'b',score:.971},{id:'a',score:.955}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.ok(result.reason,'Indistinguishable artwork should require review');
 localCards=[printings[0],{...printings[1],illustration_id:'art-1'}];
 imageResults=[{id:'a',score:.985}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.ok(result.reason,'Same artwork across editions is not enough without independent printing evidence');
 // Independent printing evidence: printed collector number and expansion code.
 localCards=printings;
 printedLines=[{zone:'bottom-left',text:'49 TWO EN',confidence:.9}];
 imageResults=[{id:'a',score:.98},{id:'b',score:.97}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.equal(result.printing?.id,'b','Matching collector number plus printed set code identifies the exact printing');
 // Artwork reprints with shared illustration: number + expansion symbol.
 localCards=[printings[0],{...printings[1],illustration_id:'art-1'}];
 printedLines=[{zone:'bottom-left',text:'49',confidence:.9}];
 imageResults=[{id:'a',score:.85}];
 symbolResults=[{set:'two',score:.975},{set:'one',score:.81}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.equal(result.printing?.id,'b','Collector number plus expansion symbol resolves shared artwork');
 // A tied symbol or an ambiguous footer may never auto-save.
 symbolResults=[{set:'two',score:.975},{set:'one',score:.96}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.ok(result.reason,'Close expansion symbol scores require review');
 printedLines=[{zone:'bottom-left',text:'12 49',confidence:.9}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.ok(result.reason,'Conflicting collector numbers require review');
 // Contradictory footer fingerprints must veto an attractive artwork result.
 localCards=printings;imageResults=[{id:'a',score:.991},{id:'b',score:.79}];
 printedLines=[{zone:'bottom-left',text:'49 ONE EN',confidence:.9}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.ok(result.reason,'Conflicting collector number and set code cannot auto-save');
 printedLines=[{zone:'bottom-left',text:'49',confidence:.9}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.ok(result.reason,'Artwork must not override conflicting collector number');
 printedLines=[];symbolResults=[];

 localCards=[printings[0]];
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.equal(context.state.cards.length,1,'Automatic batch should save without review');
 assert.equal(context.state.cards[0].id,'a');
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.equal(context.state.cards[0].qty,2,'Repeated physical scans must increment quantity');
 await run('mv60UndoLast()');
 assert.equal(context.state.cards[0].qty,1,'Undo must remove only the last auto-added copy');
 // Ambiguous edition with a strong exact name is collected without assigning
 // an arbitrary Scryfall ID, and never given an edition price.
 localCards=printings;
 printedLines=[];imageResults=[{id:'a',score:.961},{id:'b',score:.955}];symbolResults=[];
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 const provisional=context.state.cards.find(c=>c.unverifiedPrinting);
 assert.ok(provisional,'Ambiguous printing must be stored as name-only instead of demanding immediate confirmation');
 assert.equal(provisional.id,'unverified:test card');
 assert.equal(provisional.set,'');
 assert.equal(provisional.price,null);
 await run('mv60UndoLast()');
 assert.ok(!context.state.cards.some(c=>c.unverifiedPrinting),'Undo must remove a provisional scan');
 // A clearly detected token may not be saved as a normal planeswalker.
 printedToken=true;
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.ok(!context.state.cards.some(c=>c.unverifiedPrinting),'Token/type conflict must remain review-only');
 printedToken=false;
 assert.equal(await run('mv60CanSaveNameOnly({name:"Test Card",source:"ocr-candidate",ocr_reads:1})'),null,
  'One fuzzy OCR reading cannot create a provisional card');
 assert.equal(await run('mv60CanSaveNameOnly({name:"Test Card",source:"ocr-candidate",ocr_reads:2})'),'Test Card',
  'Two matching OCR reads can save a recognized name without inventing an edition');
 // A printed fraction such as 049/280 contains only one collector number.
 localCards=printings;
 const fractionLines=[{zone:'bottom-left',text:'049 / 280 TWO EN',confidence:.92}];
 assert.equal((await run(`mv61CollectorCandidates(${JSON.stringify(fractionLines)},${JSON.stringify(printings)})`)).join(','),'49',
  'Printed denominator must not be interpreted as a second collector number');
 assert.equal((await run(`mv61SetHints(${JSON.stringify(fractionLines)},${JSON.stringify(printings)})`)).join(','),'two');
 // Name matching must not imply an exact edition if neither marks nor art
 // independently distinguishes the set (including long reprint histories).
 const many=Array.from({length:16},(_,i)=>({...printings[0],id:'many'+i,set:'s'+String(i+100),collector_number:String(i+90),illustration_id:'shared'}));
 localCards=many;
 printedLines=[];symbolResults=[];imageResults=[{id:'many0',score:.97}];
 let manyOutcome=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},1)');
 assert.ok(manyOutcome.reason,'Same art across >12 editions cannot silently select an edition');
 // Stylized title rescue: two stable noisy reads + printed 0345 DFT
 // identify Webstrike Elite through the offline catalog, not name guessing.
 const webstrike={...printings[0],id:'webstrike',name:'Webstrike Elite',set:'dft',collector_number:'345'};
 localCards=[webstrike];
 printedLines=[{zone:'bottom-left',text:'R 0345 DFT EN',confidence:.85}];
 const oldCandidates=context.mvHybridCandidates;
 context.mvHybridCandidates=(name)=>name==='WEPSTRIRT'?[]:oldCandidates(name);
 let rescued=await run('mv60DecideCapture({name:"WEPSTRIRT",source:"ocr-candidate",ocr_reads:2},0)');
 assert.equal(rescued.printing?.id,'webstrike','Set code + number should rescue a stylized title');
 rescued=await run('mv60DecideCapture({name:"WEPSTRIRT",source:"ocr-candidate",ocr_reads:1},0)');
 assert.equal(!!rescued.printing,false,'One damaged title reading is not sufficient');
 rescued=await run('mv67ResolveFromPrintedMarks("ANOTHER TITLE",{lines:[{zone:"bottom-left",text:"0345 DFT EN"}]})');
 assert.equal(!!rescued.printing,false,'A contradictory title vetoes set-number resolution');
 context.mvHybridCandidates=oldCandidates;
 printedLines=[];
 localCards=printings;
 localCards=printings;
 assert.equal(await run('mv61CollectorKey("T0005")'),'t5');
 assert.equal(await run('mv61CollectorKey("0032a")'),'32a');
 console.log('v5.10.8 auto-save checks passed: exact printings, token conflict, unverified-name storage, undo, collector prefixes, and quantities.');
})().catch(e=>{console.error(e);process.exitCode=1});
