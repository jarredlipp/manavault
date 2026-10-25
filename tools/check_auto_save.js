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
const reviewCalls=[];let reviewChoice={action:"skip"};
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
 reviewNow:payload=>{const request=JSON.parse(payload);reviewCalls.push(request);if(reviewChoice)context.window.mvNativeImmediateReviewChoice({scan_id:request.scan_id,...reviewChoice});},
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
vm.createContext(context);
// The production name/face alias helpers live before the historical v5.10
// source slice. Load their real definitions instead of inventing a stub.
const aliasStart=html.indexOf('function mv129SafeFaceAlias(');
const aliasEnd=html.indexOf('function mvHybridIndex(',aliasStart);
if(aliasStart<0||aliasEnd<aliasStart)throw Error('Canonical alias helpers missing');
vm.runInContext(html.slice(aliasStart,aliasEnd),context,{filename:'alias-functions.js'});
vm.runInContext(code,context,{filename:'auto-save.js'});
async function run(expr){return vm.runInContext(expr,context)}
(async()=>{
 assert.equal(await run('mv60CanonicalMatches("Tuinvale Treefolk // Oaken Boon","Oaken Boon")'),true);
 // A real Far // Away must not be silently mistaken for Never // Return.
 const splitPhoto=[{zone:'split-title-left',text:'Far'},{zone:'split-title-right',text:'Away'}];
 assert.equal(await run(`mvSplitFaceTitlesAgree('Never // Return',${JSON.stringify(splitPhoto)})`),false,
  'Contradictory split-card titles must require immediate review');
 assert.equal(await run(`mvSplitFaceTitlesAgree('Far // Away',${JSON.stringify(splitPhoto)})`),true,
  'Both photographed split-card halves corroborate their proper identity');
 const falselyRead={...printings[0],name:'Never // Return',id:'never-return'};
 localCards=[falselyRead];printedLines=splitPhoto;
 let mismatched=await run('mv60DecideCapture({name:"Never // Return",source:"ocr-exact",ocr_reads:2},0)');
 assert.ok(mismatched.reason && !mismatched.printing,
  'Wrong exact title cannot auto-save when both photographed split-card faces disagree');
 printedLines=[];


 localCards=[printings[0]];
 let result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},0)');
 assert.equal(!!result.printing,false,'A single database printing is not evidence of the photographed edition');
 assert.ok(result.reason&&result.printings?.length===1,'Show the single candidate for immediate review');
 printedLines=[{zone:'footer-left',text:'012 ONE EN',confidence:.95}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},0)');
 assert.equal(result.printing?.id,'a','Physical set and collector evidence must resolve the single edition');
 printedLines=[];
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
 // v5.12.6: exercise the *live resolver*, not just the standalone parser.
 // Even if artwork would prefer an unrelated printing, structured footer
 // evidence settles the set, numerator, and denominator first.
 localCards=[
  {...printings[0],id:'mh1-86',set:'mh1',collector_number:'86',rarity:'common'},
  {...printings[0],id:'mh1-191',set:'mh1',collector_number:'191',rarity:'common'},
  {...printings[1],id:'mh2-254',set:'mh2',collector_number:'254',rarity:'common'}
 ];
 printedLines=[{zone:'footer-left',text:'086/254 C',pass:'color'},
  {zone:'footer-line',text:'MH1 EN',pass:'color'}];
 imageResults=[{id:'mh2-254',score:.99}];
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:2},1,{liveFast:true})');
 assert.equal(result.printing?.id,'mh1-86','Live scanner must trust structured MH1 086/254 before artwork');
 assert.equal(result.method,'structured set + collector numerator');
 printedLines.push({zone:'footer-deep',text:'MH2 EN',pass:'color'});
 result=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:2},1,{liveFast:true})');
 assert.ok(!result.printing&&result.reason,'Conflicting printed sets must never auto-verify');
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
 printedLines=[{zone:'footer-left',text:'012 ONE EN',confidence:.95}];
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.equal(context.state.cards.length,1,'Automatic batch should save without review');
 assert.equal(context.state.cards[0].id,'a');
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.equal(context.state.cards[0].qty,2,'Repeated physical scans must increment quantity');
 await run('mv60UndoLast()');
 assert.equal(context.state.cards[0].qty,1,'Undo must remove only the last auto-added copy');
 printedLines=[];
 // Ambiguous printings must be reviewed immediately while CameraX is open.
 // Vintage / pre-collector-number cards must be able to use independent
 // bundled set-symbol evidence to choose a unique edition. No number is read.
 localCards=printings;printedLines=[];
 symbolResults=[{set:'one',score:.985},{set:'two',score:.68}];
 let noNumber=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},0,{liveFast:true})');
 assert.equal(noNumber.printing?.id,'a','Clear offline symbol + exact title must resolve a vintage card without collector number');
 symbolResults=[{set:'one',score:.952},{set:'two',score:.93}];
 noNumber=await run('mv60DecideCapture({name:"Test Card",source:"ocr-exact",ocr_reads:1},0,{liveFast:true})');
 assert.ok(!noNumber.printing&&noNumber.reason,'Indistinguishable set symbols must not force an edition');
 symbolResults=[];
 // The user may explicitly choose to save an unverified name, never silently.
 localCards=printings;
 printedLines=[];imageResults=[{id:'a',score:.961},{id:'b',score:.955}];symbolResults=[];
 reviewChoice={action:'unverified'};
 await run('window.mvNativeScanBatch([{scan_id:"ambiguous-1",scan_index:0,name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.equal(reviewCalls.at(-1)?.allow_unverified,true,'Name-known ambiguity should allow explicit unverified choice');
 assert.equal(reviewCalls.at(-1)?.choices.length,2,'Review should offer candidate printings right now');
 const provisional=context.state.cards.find(c=>c.unverifiedPrinting);
 assert.ok(provisional,'An explicitly approved unverified scan should be stored without guessing its printing');
 assert.equal(provisional.id,'unverified:test card');
 assert.equal(provisional.set,'');
 assert.equal(provisional.price,null);
 await run('mv60UndoLast()');
 assert.ok(!context.state.cards.some(c=>c.unverifiedPrinting),'Undo must remove a provisional scan');
 // A clearly detected token may not be saved as a normal planeswalker.
 printedToken=true;reviewChoice={action:'skip'};
 await run('window.mvNativeScanBatch([{scan_id:"token-test-1",scan_index:0,name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.ok(!context.state.cards.some(c=>c.unverifiedPrinting),'Token/type conflict must be reviewed and explicitly skipped');
 printedToken=false;
 assert.equal(await run('mv60CanSaveNameOnly({name:"Test Card",source:"ocr-candidate",ocr_reads:1})'),null,
  'One fuzzy OCR reading cannot create a provisional card');
 assert.equal(await run('mv60CanSaveNameOnly({name:"Test Card",source:"ocr-candidate",ocr_reads:2})'),null,
  'Two OCR candidate readings alone cannot create a collection entry');
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
 assert.equal(rescued.printing,undefined,'Wrong or unrecognized title must not auto-save even with footer marks');
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
 // v5.10.9: paired footer must outrank stray artwork/rules OCR numerals,
 // but never assign an edition from a copyright year or conflicting marks.
 const printed={id:'dft345',name:'Webstrike Elite',set:'dft',collector_number:'345'};
 const other={id:'other',name:'Webstrike Elite',set:'abc',collector_number:'345'};
 let pair=await run(`mv109FooterPairs([{zone:'footer-left',text:'R 0345 DFT EN'}],${JSON.stringify([printed,other])})`);
 assert.equal(pair?.printing?.id,'dft345','Collector/set pair in genuine footer should resolve edition');
 pair=await run(`mv109FooterPairs([{zone:'footer-left',text:'RO345 DFT EN'}],${JSON.stringify([printed,other])})`);
 assert.equal(pair?.printing?.id,'dft345','Rarity + O-for-zero OCR should recover collector number');
 pair=await run(`mv109FooterPairs([{zone:'footer-left',text:'© 2025 Wizards of the Coast DFT'}],${JSON.stringify([printed,other])})`);
 assert.equal(pair,null,'Copyright notices must not act as printing evidence');
 pair=await run(`mv109FooterPairs([{zone:'type-bar',text:'R 0345 DFT EN'}],${JSON.stringify([printed,other])})`);
 assert.equal(pair,null,'Set+collector must come from footer, never rules text');
 pair=await run(`mv109FooterPairs([{zone:'footer-left',text:'R 0345 DFT EN'},{zone:'footer-left',text:'R 0345 ABC EN'}],${JSON.stringify([printed,other])})`);
 assert.equal(pair,null,'Conflicting trusted footer lines must not silently pick a printing');
 // When the printing cannot be verified, there MUST be no collection
 // mutation or saved receipt until the native foreground review is answered.
 localCards=printings;printedLines=[];
 imageResults=[{id:'a',score:.981},{id:'b',score:.979}];symbolResults=[];
 reviewChoice=null;
 const beforeReviewA=context.state.cards.find(c=>c.id==='a')?.qty||0;
 const beforeReviewB=context.state.cards.find(c=>c.id==='b')?.qty||0;
 const reviewBefore=reviewCalls.length;
 await run('window.mvNativeScanLive({scan_id:"await-review-1",scan_index:0,name:"Test Card",source:"ocr-exact",ocr_reads:2})');
 for(let cycle=0;cycle<20 && reviewCalls.length===reviewBefore;cycle++)await new Promise(r=>setImmediate(r));
 assert.equal(reviewCalls.length,reviewBefore+1,'Ambiguous card must open review DURING scan');
 assert.equal(context.state.cards.find(c=>c.id==='a')?.qty||0,beforeReviewA,'Collection must wait for an immediate printing selection');
 assert.equal(context.state.cards.find(c=>c.id==='b')?.qty||0,beforeReviewB,'No edition may be silently assigned before review');
 context.window.mvNativeImmediateReviewChoice({scan_id:'await-review-1',action:'printing',printing_id:'b'});
 await run('mvLiveRunning');
 assert.equal(context.state.cards.find(c=>c.id==='b')?.qty||0,beforeReviewB+1,'Selected edition must save before next scan');
 reviewChoice={action:'skip'};
 // v5.11.0: a capture is persisted as soon as CameraX emits it;
 // pressing Finish must NOT be required to trigger printing resolution.
 localCards=[printings[0]];printedLines=[{zone:'footer-left',text:'012 ONE EN',confidence:.95}];imageResults=[];symbolResults=[];
 const liveReceipts=[];
 context.window.ManaVaultNativeScanner.scanResolved=payload=>liveReceipts.push(JSON.parse(payload));
 const beforeLive=context.state.cards.find(c=>c.id==='a')?.qty||0;
 await run('window.mvNativeScanLive({scan_id:"live-session-1",scan_index:0,name:"Test Card",source:"ocr-exact",ocr_reads:2})');
 await run('mvLiveRunning');
 const afterLive=context.state.cards.find(c=>c.id==='a')?.qty||0;
 assert.equal(afterLive,beforeLive+1,'Live CameraX event must save immediately before Finish');
 assert.equal(liveReceipts.at(-1)?.status,'verified','Camera receives an acknowledgement after save');
 await run('window.mvNativeScanBatch([{scan_id:"live-session-1",scan_index:0,name:"Test Card",source:"ocr-exact",ocr_reads:2}])');
 assert.equal(context.state.cards.find(c=>c.id==='a')?.qty,afterLive,'Finish/replay must not save a second copy');
 assert.match(fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/manavault/app/NativeScannerActivity.java'),'utf8'),/MainActivity\.deliverLiveCapture\(result\)/);
 assert.match(fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/manavault/app/MainActivity.java'),'utf8'),/mvNativeScanLive/);
 assert.match(fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/manavault/app/NativeScannerActivity.java'),'utf8'),/resolvedScanIds\.size\(\)>=captured\.size\(\)/);
 console.log('v5.11.3 live-save checks passed: one-at-a-time commits, immediate review, selected edition persistence and replay protection.');
 console.log('v5.10.9 auto-save checks passed: footer pair matching, copyright exclusion, conflicts, and existing scanner cases.');
})().catch(e=>{console.error(e);process.exitCode=1});
