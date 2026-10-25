/* Safety: bad native title must not auto-save; matching printed ID may suggest a review. */
const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv613NonTitleText('),stop=html.indexOf('// Prefer a set code printed beside',start);
const decideStart=html.indexOf('async function mv60DecideCapture(');
const decideEnd=html.indexOf(' await mvHybridLoadNames();',decideStart);
assert(start>=0&&stop>start&&decideEnd>decideStart);
const mkCtx=found=>({
 mvHybridNorm:s=>String(s||'').toLowerCase().replace(/[^a-z0-9 ]/g,' ').trim(),
 mv61Request:async()=>({lines:[{text:'DGM EN',zone:'footer-left'}]}),
 mv67ResolveFromPrintedMarks:async()=>found,
 mv67TitleAgrees:()=>false,mvLogCamera:()=>{},
});
async function run(raw,found){
 const ctx=mkCtx(found);vm.createContext(ctx);
 vm.runInContext(html.slice(start,stop),ctx);
 vm.runInContext(html.slice(html.indexOf('function mv133FooterFirstRequired('),decideStart),ctx);
 vm.runInContext(html.slice(decideStart,decideEnd)+' return {reason:"title passed guard"};\n}',ctx);
 return ctx.mv60DecideCapture({name:raw,source:'ocr-candidate',ocr_reads:2},0,{liveFast:true});
}
(async()=>{
 for(const bad of ['but as long as injustice exists, I\'m still needed','Each other creature you control with a','Cycling']){
  const outcome=await run(bad,{printing:{id:'wrong-autosave'},printings:[]});
  assert.equal(outcome.printing,undefined,bad+' must never auto verify');
 }
 const seen=await run('but as long as injustice exists, I\'m still needed',
   {printings:[{id:'cap',name:'Captain America, Living Legend',set:'msh',collector_number:'210'}]});
 assert.equal(seen.printing,undefined);
 assert.equal(seen.printings?.[0].id,'cap','print suggestion must remain review-only');
 console.log('Non-title footer review-only rescue gates passed');
})().catch(e=>{console.error(e);process.exit(1)});
