/** v5.12.33: never trust a speculative multi-face title over physical footer. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv133FooterFirstRequired(');
const end=html.indexOf('async function mv60DecideCapture(',start);
assert(start>0&&end>start,'Footer-first functions must be present');
const calls=[];
const ctx={mv61Request:async (...args)=>{calls.push(args);return {lines:[{zone:'footer-left',text:'R 0345 DFT EN'}]}},
 mv67ResolveFromPrintedMarks:async (title)=>({reason:'Footer suggests Webstrike Elite; review',
  printings:[{name:'Webstrike Elite',set:'dft',collector_number:'345'}],suggestion:'Webstrike Elite'}),
 mvLogCamera:()=>{},mv613NonTitleText:()=>false,mv67TitleAgrees:()=>true,console};
vm.createContext(ctx);vm.runInContext(html.slice(start,end),ctx);
assert.equal(vm.runInContext("mv133FooterFirstRequired({source:'ocr-exact',name:'Savannah Lions'})",ctx),false);
assert.equal(vm.runInContext("mv133FooterFirstRequired({source:'ocr-candidate',name:'Who // What // When // Where // Why'})",ctx),true);
assert.equal(vm.runInContext("mv133FooterFirstRequired({source:'ocr-exact',name:'Who // What // When // Where // Why'})",ctx),true);
assert.equal(vm.runInContext("mv133FooterFirstRequired({source:'manual-title',name:'Who // What // When // Where // Why'})",ctx),false);
(async()=>{
 const uncertain={source:'ocr-candidate',name:'Who // What // When // Where // Why'};
 const review=await ctx.mv133FooterFirstIdentity(uncertain,12,true);
 assert.equal(review.printings[0].name,'Webstrike Elite');
 assert.equal(review.printing,undefined,'Wrong identity must not auto-save');
 assert.equal(calls[0][0],'readPrintingMarks');
 assert.equal(calls[0][3].footerFirst,true);
 ctx.mv67ResolveFromPrintedMarks=async()=>({printing:{name:'Webstrike Elite',set:'dft',collector_number:'345'}});
 const stillReview=await ctx.mv133FooterFirstIdentity({source:'ocr-exact',name:'Who // What // When // Where // Why'},12,true);
 assert.equal(stillReview.printing,undefined,'Even a title/footer match cannot silently confirm >2 faces without visual title evidence');
 const real=await ctx.mv133FooterFirstIdentity({source:'ocr-candidate',name:'Webstrike Elite'},12,true);
 assert.equal(real.printing.name,'Webstrike Elite','Independent title/footer corroboration can verify a provisional title');
 const artOnly=await ctx.mv133FooterFirstIdentity({source:'art-index-suggestion',name:'Webstrike Elite'},12,true);
 assert.equal(artOnly.printing,undefined,'Art-derived name without actual title OCR must never auto-save');
 assert.equal(artOnly.printings[0].name,'Webstrike Elite');
 ctx.mv67ResolveFromPrintedMarks=async()=>({reason:'Collector unclear'});
 const missing=await ctx.mv133FooterFirstIdentity(uncertain,12,true);
 assert.equal(missing.printing,undefined);
 assert.match(missing.reason,/Correct the title/);
 console.log('v5.12.33 footer-first independent identity and split-card false-match veto PASSED');
})().catch(e=>{console.error(e);process.exitCode=1});
