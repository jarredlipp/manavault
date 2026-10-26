'use strict';
const fs=require('node:fs'),assert=require('node:assert/strict'),vm=require('node:vm');
const base='app/src/main/java/com/manavault/app/';
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const native=fs.readFileSync(base+'NativeScannerActivity.java','utf8');
const footer=fs.readFileSync(base+'PrintingFingerprintVerifier.java','utf8');
const symbols=fs.readFileSync(base+'SymbolShapeMatcher.java','utf8');
const gate=fs.readFileSync(base+'StackChangeGate.java','utf8');
assert.match(footer,/candidateWideRescueAttempted/);
assert.match(footer,/"footer-wide","candidate-contrast"/);
assert.match(footer,/CollectorOcrPolicy\.hasCandidateNumber/);
assert.match(html,/const valid=new Set\(\['footer-left','footer-line','footer-deep','footer-wide'/);
assert.match(symbols,/extractColorInk\(int\[\] rgb\)/);
assert.match(footer,/SymbolShapeMatcher\.extractColorInk\(rgb\)/);
assert.match(footer,/\.825f,\.885f/);
assert.match(gate,/noisyStrongReads>=5 && now-noisyStrongAt>=480/);
assert.match(native,/Stack transition pending/);
assert.match(html,/Incomplete full-artwork index candidate coverage/);
let request;
let response={matches:[],failed:0};
const ctx={console,setTimeout,clearTimeout,mv60RankSerial:0,mv60RankWaiters:new Map(),
 window:{ManaVaultNativeScanner:{fullArtworkStatus:()=> 'Complete printing-level artwork index: 111301 faces',
 selectImage:()=>{},rankBundledArtwork:(input)=>{
  request=JSON.parse(input);
  Promise.resolve().then(()=>{
   const pending=ctx.mv60RankWaiters.get(request.requestId);
   if(pending){clearTimeout(pending.timer);pending.resolve(response);}
  });
 }}}};ctx.ManaVaultNativeScanner=ctx.window.ManaVaultNativeScanner;
vm.createContext(ctx);
const begin=html.indexOf('async function mv115RankBundledArtwork(');
const end=html.indexOf('// v5.10.1:',begin);
assert(begin>=0&&end>begin);
vm.runInContext(html.slice(begin,end),ctx);
const printings=[
 {id:'a',illustration_id:'one',set:'xyz',collector_number:'12'},
 {id:'b',illustration_id:'two',set:'xyz',collector_number:'19'}];
(async()=>{
 response={failed:0,matches:[
  {id:'a',score:.890,artScore:.95,textScore:.94,footerScore:.81},
  {id:'b',score:.874,artScore:.90,textScore:.85,footerScore:.80}]};
 let result=await ctx.mv115RankBundledArtwork(0,printings,{requireFooter:true});
 assert.equal(result.printing,undefined,'Near ties MUST NOT auto-save');
 assert.deepEqual([...result.advisoryOrder.map(x=>x.id)],['a','b'],'Near tie must retain visual review order');
 response={failed:1,matches:[{id:'a',score:.91,textScore:.86}]};
 result=await ctx.mv115RankBundledArtwork(0,printings,{requireFooter:false});
 assert.equal(result.printing,undefined,'Missing artwork must not auto-save');
 assert.equal(result.advisoryOrder.length,1,'Missing coverage should retain review hint');
 console.log('v5.13.6 footer/symbol/scene and artwork advisory-not-authority tests PASS');
})().catch(e=>{console.error(e);process.exitCode=1});
