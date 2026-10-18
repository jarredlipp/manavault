const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const text=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const code=text.slice(text.indexOf('function mv67TitleAgrees('),text.indexOf('function mvSplitFaceTitlesAgree('));
const norm=v=>String(v).toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
const edit=(a,b)=>{let p=Array.from({length:b.length+1},(_,i)=>i);for(let i=1;i<=a.length;i++){let q=[i];for(let j=1;j<=b.length;j++)q[j]=Math.min(q[j-1]+1,p[j]+1,p[j-1]+(a[i-1]===b[j-1]?0:1));p=q}return p[b.length]};
const card={id:'webstrike-printing',name:'Webstrike Elite',set:'dft',collector_number:'345'};
const db={count:async()=>118773,bySetCollector:async(s,n)=>s==='dft'&&['0345','345'].includes(n)?card:null};
const context={mvHybridNorm:norm,mvHybridEdit:edit,mv61CollectorKey:t=>String(Number(t)),ManaVaultCardDB:db,
 window:{ManaVaultCardDB:db,ManaVaultNativeScanner:{nativeCatalogStatus:()=> 'Ready: 118773'}}};
vm.createContext(context);vm.runInContext(code,context);
(async()=>{
 let result=await vm.runInContext('mv67ResolveFromPrintedMarks("WEPSTRIKE", {lines:[{zone:"footer-left",text:"RO345 DFT EN"}]})',context);
 assert.equal(result.printing?.id,card.id,'stylized title + rarity-prefixed OCR number and set code');
 result=await vm.runInContext('mv67ResolveFromPrintedMarks("UNRELATED NAME", {lines:[{zone:"footer-left",text:"RO345 DFT EN"}]})',context);
 assert.equal(result.printing,undefined,'contradictory title must veto matching collector/set');
 console.log('Webstrike Elite RO345 / DFT footer rescue and contradictory-title protection passed.');
})().catch(e=>{console.error(e);process.exit(1)});
