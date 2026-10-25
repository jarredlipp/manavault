/** Focused behavior regression for footer-first correction & review plumbing. */
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv133FooterFirstRequired(');
const end=html.indexOf('async function mv60DecideCapture(',start);
assert(start>0 && end>start);
const events=[];
const context={
 mv61Request:async (...args)=>{events.push(args);return {lines:[{zone:'footer-left',text:'EN'}]};},
 mv67ResolveFromPrintedMarks:async()=>({reason:'Unclear printed collector'}),
 mv120TextEvidence:async()=>({suggestion:'Webstrike Elite',words:4}),
 mvHybridNorm:x=>String(x||'').toLowerCase(),
 mvLogCamera:()=>{},mv613NonTitleText:()=>false,mv67TitleAgrees:()=>false,
 console
};
vm.createContext(context);
vm.runInContext(html.slice(start,end),context);
(async()=>{
 const wrong={name:'Who // What // When // Where // Why',source:'ocr-candidate'};
 const result=await context.mv133FooterFirstIdentity(wrong,4,true);
 assert.equal(result.printing,undefined,'Rules text must NEVER create an auto-save');
 assert.equal(result.suggestion,'Webstrike Elite','Helpful manual candidate is returned');
 assert.match(result.reason,/rules|type/i);
 const direct=await context.mv133FooterFirstIdentity({name:'Raggadragga, Goreguts Boss',source:'ocr-candidate'},3,true);
 assert.equal(direct.printing,undefined);
 assert.match(html,/outcome\.suggestion.*mv60CollectPrintings/s,'Suggestion must reach review choices');
 assert.match(html,/captured\.source==='ocr-split-provisional'/,'Split panel scan uses extended timeout');
 assert.match(html,/mvSplitFaceTitlesAgree\(raw,evidence\.lines\)/,'No unverifiable sideways title may save');
 assert.match(html,/quality_glare_pct/,'Quality diagnostics preserved');
 console.log('v5.13.0 candidate review + identity safety PASS');
})().catch(e=>{console.error(e);process.exitCode=1});
