/** Regression: alternate title OCR, misleading artist/footer OCR and review suggestions. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const segment=(a,b)=>{const s=html.indexOf(a),e=html.indexOf(b,s);assert(s>=0 && e>s,a);return html.slice(s,e)};
const ctx={window:{ManaVaultNativeScanner:{nativeCatalogStatus:()=> 'Ready: 118893 offline native printings',lookupOfflinePrintings:(kind,key)=>({set:{dft:1}}[kind]?.[key]?JSON.stringify({ready:true,rows:[{id:'ok'}]}):JSON.stringify({ready:true,rows:[]}))}},
 ManaVaultCardDB:{bySetCollectorAll:async (set,number)=> (set==='dft'&&String(Number(number))==='345'?[{id:'dft345',name:'Webstrike Elite',set:'dft',collector_number:'345',type_line:'Creature — Insect Archer'}]:[])},
 console};
ctx.ManaVaultCardDB.bySetCollector=async()=>null;ctx.window.ManaVaultCardDB=ctx.ManaVaultCardDB;vm.createContext(ctx);
vm.runInContext(segment('function mvHybridNorm(', 'let mvHybridBuckets=').replace(/^function mvHybridNorm/, 'function mvHybridNorm')+
 segment('function mv61CollectorKey(', 'function mv61NormalImage(')+
 segment('function mv67TitleAgrees(', 'function mvSplitFaceTitlesAgree('),ctx);
const evalExpr=x=>vm.runInContext(x,ctx);
assert.equal(evalExpr("mv613NonTitleText('DFT *ENSAM McKENZIE')"),true);
assert.equal(evalExpr("mv613NonTitleText('© 2025 Wizards of the Coast')"),true);
assert.equal(evalExpr("mv613NonTitleText('Of the Coast')"),true);
assert.equal(evalExpr("mv613NonTitleText('Webstrike Elite')"),false);
assert.equal(evalExpr("mv67TitleAgrees('WEBSTRIKE','Webstrike Elite')"),true);
assert.equal(evalExpr("mv67TitleAgrees('DFT *ENSAM McKENZIE','Webstrike Elite')"),false);
async function rescue(title){ctx.scanned=[{zone:'footer-left',text:'R 0345'},
 {zone:'footer-line',text:'DFT *EN SAM McKENZIE'},
 {zone:'footer-deep',text:'©2025 Wizards of the Coast'}];
 ctx.title=title;return await vm.runInContext('mv67ResolveFromPrintedMarks(title,{lines:scanned})',ctx)}
(async()=>{
 let result=await rescue('WEBSTRIKE');
 assert.equal(result.printing?.id,'dft345','independently recognized first word + physical identifier');
 result=await rescue('DFT *ENSAM McKENZIE');
 assert.equal(result.printing,undefined,'footer OCR cannot auto-save');
 assert.equal(result.printings?.[0]?.id,'dft345','offer candidate printing instead of blank review');
 result=await rescue('Unrecognized title');
 assert.equal(result.printings?.[0]?.name,'Webstrike Elite','no title should still get a footer-ranked suggestion');
 result=await rescue('Of the Coast');
 assert.equal(result.printing,undefined,'copyright footer phrase cannot auto-save');
 ctx.ManaVaultCardDB.bySetCollectorAll=async()=>[{id:'v1',name:'Webstrike Elite',set:'dft',collector_number:'345'},
  {id:'v2',name:'Webstrike Elite',set:'dft',collector_number:'345'}];
 result=await rescue('WEBSTRIKE');
 assert.equal(result.printing,undefined,'colliding editions not auto-resolved');
 assert.equal(result.printings?.length,2,'colliding editions appear for explicit review');
 const overlay=fs.readFileSync(__dirname+'/../app/src/main/java/com/manavault/app/CardEdgeOverlay.java','utf8');
 const scanner=fs.readFileSync(__dirname+'/../app/src/main/java/com/manavault/app/NativeScannerActivity.java','utf8');
 assert(overlay.includes('at(upright,.978f,.247f)'),'overlay guides full actual title crop');
 assert(scanner.includes('height()*.235f') || scanner.includes('getHeight()*.235f'),'two-line title crop');
 assert(scanner.includes('Unrecognized title'),'unreadable captures continue to review instead of hang');
 assert(scanner.includes('titleImage(rotated,enhancedPass)'),'color OCR rescue available');
 console.log('v5.12.13 title/footer tests passed: no phantom names, footer review, variants and exact-title performance.');
})().catch(e=>{console.error(e);process.exitCode=1});
