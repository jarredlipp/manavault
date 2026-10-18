/** Fast reference-text fallback: offline, no guessed printing, rejects conflicting titles. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const start=source.indexOf('// v5.12: use indexed offline rules/type reference text');
const end=source.indexOf('async function mv60DecideCapture',start);
assert.ok(start>0&&end>start,'offline reference matcher installed');
const rulesByName={
 'Light of Day':{name:'Light of Day',oracle_text:'Black creatures cannot attack or block.',type_line:'Enchantment'},
 'Far // Away':{name:'Far // Away',oracle_text:'Return target creature to its owner\'s hand. Target player sacrifices a creature.',type_line:'Instant'}
};
let sourceText='Black creatures cannot attack or block';
const context={window:{ManaVaultNativeScanner:{searchReferenceText:()=>JSON.stringify({ready:true,rows:[rulesByName['Light of Day']]})}},
 mv61Resolve(){},mv61Request:async()=>({referenceText:sourceText}),
 mvHybridNorm:n=>String(n||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim(),
 mvHybridEdit:(a,b)=>{let p=Array.from({length:b.length+1},(_,i)=>i);for(let i=1;i<=a.length;i++){let q=[i];for(let j=1;j<=b.length;j++)q[j]=Math.min(q[j-1]+1,p[j]+1,p[j-1]+(a[i-1]===b[j-1]?0:1));p=q}return p[b.length]}

};
vm.createContext(context);vm.runInContext(source.slice(start,end),context);
(async()=>{
 const one=await context.mv120TextEvidence(0,'Lighr of Day');
 assert.equal(one?.identity,'Light of Day','rules corroborate an imperfect title');
 context.window.ManaVaultNativeScanner.searchReferenceText=()=>JSON.stringify({ready:true,rows:[rulesByName['Far // Away']]});
 sourceText='Return target creature to its owners hand. Target player sacrifices a creature';
 const two=await context.mv120TextEvidence(0,'Never // Return');
 assert.notEqual(two?.identity,'Far // Away','contradictory exact title cannot be automatically replaced');
 assert.equal(two?.suggestion,'Far // Away','offer grounded correction during review');
 console.log('Offline OCR reference matcher: corrects a mild misread and refuses a conflicting split-card identity');
})().catch(e=>{console.error(e);process.exit(1)});
