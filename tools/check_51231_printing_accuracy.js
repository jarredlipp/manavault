// Full SQLite lookups must not select visually identical printings from art alone.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const start=html.indexOf('async function mv115RankBundledArtwork(');
const end=html.indexOf('// v5.10.1:',start);
assert(start>0 && end>start);
const functionBody=html.slice(start,end);
let sent=null;
const context={console,mv60RankSerial:0,mv60RankWaiters:new Map(),setTimeout,clearTimeout,
 window:{ManaVaultNativeScanner:{fullArtworkStatus:()=> 'Complete printing-level artwork index: 121000 faces',
   selectImage:()=>{},rankBundledArtwork:(request)=>{
    sent=JSON.parse(request);
    const answers=sent.choices.map((c,i)=>({id:c.id,score:[.985,.981,.74][i]}));
    Promise.resolve().then(()=>{const waiting=context.mv60RankWaiters.get(sent.requestId);clearTimeout(waiting.timer);waiting.resolve({matches:answers,failed:0});});
   }}},
};
context.ManaVaultNativeScanner=context.window.ManaVaultNativeScanner;
vm.createContext(context);
// Evaluate helper directly, ungrouped printings must retain every ID.
vm.runInContext(functionBody,context);
(async()=>{
 const common={name:'Test Collector Variant',illustration_id:'same-original-art',set:'abc',frame_effects:[]};
 const prints=[{...common,id:'first',collector_number:'12'},
   {...common,id:'second',collector_number:'13'},
   {...common,id:'different-art',illustration_id:'another-art',collector_number:'14'}];
 const response=await context.mv115RankBundledArtwork(0,prints,{requireFooter:true,allowSharedArtwork:true});
 assert.equal(sent.choices.length,3,'Same-illustration printings were deduplicated');
 assert.deepEqual(Array.from(response.winningIds||[]),['first','second']);
 assert.equal(response.printing,undefined,'Shared illustration must not choose an exact printing');
 assert.equal(response.sharedIllustration,true);
 console.log('v5.12.31 candidate-scoped full SQLite comparisons: shared-art IDs preserved, no art-only exact edition selection PASSED');
})().catch(e=>{console.error(e);process.exitCode=1});
