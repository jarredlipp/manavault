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
const printings=[
 {id:'a',name:'Test Card',lang:'en',digital:false,nonfoil:true,foil:false,set:'one',set_name:'One Set',collector_number:'12',image_url:'https://cards.scryfall.io/art1.jpg',illustration_id:'art-1'},
 {id:'b',name:'Test Card',lang:'en',digital:false,nonfoil:true,foil:false,set:'two',set_name:'Two Set',collector_number:'49',image_url:'https://cards.scryfall.io/art2.jpg',illustration_id:'art-2'}
];
const context={
 window:{},console,
 setTimeout:()=>1,clearTimeout:()=>{},
 navigator:{onLine:false},
 ManaVaultCardDB:{count:async()=>118773,printings:async()=>localCards},
 mvHybridNorm:x=>String(x||'').toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim(),
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
 assert.match(result.reason,/share this illustration/,'Same artwork across editions is not enough');
 localCards=[printings[0]];
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.equal(context.state.cards.length,1,'Automatic batch should save without review');
 assert.equal(context.state.cards[0].id,'a');
 await run('window.mvNativeScanBatch([{name:"Test Card",source:"ocr-exact",ocr_reads:1}])');
 assert.equal(context.state.cards[0].qty,2,'Repeated physical scans must increment quantity');
 await run('mv60UndoLast()');
 assert.equal(context.state.cards[0].qty,1,'Undo must remove only the last auto-added copy');
 console.log('v5.10 automatic-saving checks passed: unique printing, uncertain OCR, clear artwork, tied artwork, shared illustration, and quantity increment.');
})().catch(e=>{console.error(e);process.exitCode=1});
