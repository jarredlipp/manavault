/** v5.11.9: no ready catalog must never strand correctly OCR-identified cards. */
'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const start=html.indexOf('// v5.10: auto-save');
const code=html.slice(start,html.indexOf('</script>',start));
let saved=[],receipts=[],reviews=[];
let catalogReady=false;
const context={
 window:{},console,setTimeout:()=>1,clearTimeout:()=>{},
 navigator:{onLine:false},
 ManaVaultCardDB:{count:async()=>0,fastPrintings:async()=>({rows:[],complete:false,source:'Catalog warming'}),
     bySetCollector:async()=>null},
 mvHybridNorm:v=>String(v||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim(),
 mvHybridEdit:(a,b)=>{let p=Array.from({length:b.length+1},(_,i)=>i);for(let i=1;i<=a.length;i++){let q=[i];for(let j=1;j<=b.length;j++)q[j]=Math.min(q[j-1]+1,p[j]+1,p[j-1]+(a[i-1]===b[j-1]?0:1));p=q}return p[b.length]},
 mvHybridLoadNames:async()=>{},
 mvHybridCandidates:name=>name==='Light of Day'?[{name,score:1,exact:true}]:[],
 mvLogCamera:()=>{},state:{cards:saved,decks:[]},
 scanAlsoOwn:true,insertIntoDeck:()=>false,
 info:p=>({id:p.id,name:p.name,qty:1}),mv53PrintingPrice:()=>null,
 localStorage:{getItem:()=>null,setItem:()=>{}},STORE:'test',
 mvCacheReferenceArtwork:async()=>{},scanTone:()=>{},
 render:()=>{},tab:'Scan cards',scanner:()=>'',bind:()=>{},save:()=>{},scanDeckIndex:-1,
 document:{getElementById:()=>null},mv58NativeStatus:()=>{},mv59Next:()=>{},
 mv59Queue:[],mv59Cursor:-1,scry:async()=>{throw Error('Should not request network')}
};
context.window.ManaVaultCardDB=context.ManaVaultCardDB;
context.window.ManaVaultNativeScanner={
 nativeCatalogStatus:()=>catalogReady?'Ready: 118773 offline native printings':'Preparing offline catalog',
 lookupOfflinePrintings:()=>JSON.stringify({ready:false,complete:false,rows:[]}),
 scanResolved:payload=>receipts.push(JSON.parse(payload)),
 scanProgress:()=>{},
 reviewNow:payload=>{reviews.push(JSON.parse(payload));throw Error('No review should occur with reliable exact name during catalog warmup')},
 selectImage:()=>{},
 readPrintingMarks:raw=>{let data=JSON.parse(raw);context.window.mv61OnMarksResult({requestId:data.requestId,lines:[],token:false})}
};
context.ManaVaultNativeScanner=context.window.ManaVaultNativeScanner;
vm.createContext(context);vm.runInContext(code,context);
(async()=>{
 const scan={scan_id:'test-1',scan_index:0,name:'Light of Day',source:'ocr-exact',ocr_reads:2};
 context.window.mvNativeScanLive(scan);
 await vm.runInContext('mvLiveRunning',context);
 assert.equal(saved.length,1,'recognized card must be stored even if bundled catalog is temporarily unavailable');
 assert.equal(saved[0].unverifiedPrinting,true,'no guessed edition');
 assert.equal(saved[0].name,'Light of Day');
 assert.equal(receipts.length,1,'collection write must be acknowledged');
 assert.equal(receipts[0].status,'unverified');
 assert.equal(reviews.length,0,'do not show empty review when catalog is initializing');
 context.window.mvNativeScanLive(scan);
 await vm.runInContext('mvLiveRunning',context);
 assert.equal(saved[0].qty,1,'same scan ID must not create duplicate');
 assert.ok(receipts.length>=2);
 // Truly unrecognized titles may not be saved as a guessed name.
 context.window.ManaVaultNativeScanner.reviewNow=payload=>{
  const data=JSON.parse(payload);reviews.push(data);
  context.window.mvNativeImmediateReviewChoice({scan_id:data.scan_id,action:'skip'});
 };
 context.window.mvNativeScanLive({...scan,scan_id:'test-2',name:'garbled 123',source:'ocr-candidate'});
 await vm.runInContext('mvLiveRunning',context);
 assert.equal(saved.length,1,'unknown title must not be recorded as a card');
 assert.equal(reviews.length,1,'unknown title should receive correction/skip review');
 console.log('Catalog-not-ready recovery: saved exact name before Finish, no guessed printing, no empty review, no duplicate; unknown identity requires review.');
})().catch(e=>{console.error(e);process.exit(1)});
