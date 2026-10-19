/** Fast-scanner regression cases from the October 19 phone logs. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const row=(text,zone='footer-left')=>({text,zone,pass:'color'});
const from=html.indexOf('function mv61CollectorKey('),to=html.indexOf('function mv61NormalImage(');
assert(from>0&&to>from);
const ctx={};vm.createContext(ctx);vm.runInContext(html.slice(from,to),ctx);
function decide(lines,printings,extra={}){
 ctx.lines=lines;ctx.printings=printings;ctx.extra=extra;
 return vm.runInContext('mv126ResolveStructuredFooter(lines,printings,extra)',ctx);
}
const aven=[{id:'frf6',name:'Aven Skirmisher',set:'frf',collector_number:'6',rarity:'common'},
 {id:'other6',name:'Aven Skirmisher',set:'cma',collector_number:'6',rarity:'common'}];
assert.equal(decide([row('185'),row('FRF EN','footer-line')],aven).printing?.id,'frf6',
 'denominator-only reading must not block a unique named printing in FRF');
assert.equal(decide([row('C 0185'),row('FRF EN','footer-line')],aven).printing,undefined,
 'rarity-prefixed contradictory collector must veto set-only inference');
assert.equal(decide([row('003/185'),row('FRF EN','footer-line')],aven).printing,undefined,
 'a contradictory complete collector fraction must veto');
assert.equal(decide([row('185'),row('FRF EN','footer-line')],[aven[0],{...aven[0],id:'alt'}]).printing,undefined,
 'two same-set variants still require review');
assert.equal(decide([row('185'),row('FRF EN','footer-line')],aven,{titleVerified:false}).printing,undefined,
 'set-only fallback requires an exact trusted title');
assert.equal(decide([row('006/185 C'),row('FRF EN','footer-line')],aven).printing?.id,'frf6',
 'complete collector footer stays on fast path');
const jace=[{id:'jace-token',name:'Jace',set:'fea',collector_number:'T0005',type_line:'Token Planeswalker — Jace'}];
assert.equal(decide([row('T 0005'),row('FEA EN','footer-line')],jace).printing?.id,'jace-token',
 'token collector T 0005 must match the indexed T0005 printing');
const maybeCatalog=JSON.stringify({ready:true,rows:[
 {id:'jace-token',name:'Jace',type_line:'Token Planeswalker — Jace',collector_number:'T0005',set:'fea',digital:false}
]});
let hits=0;
const tokenCtx={window:{ManaVaultNativeScanner:{lookupOfflinePrintings:(kind,key,limit)=>{
 assert.equal(kind,'name');assert.equal(key,'jace');assert.equal(limit,64);hits++;return maybeCatalog;}}},
 mvLogCamera:()=>{},localStorage:{getItem:()=>null},console};
vm.createContext(tokenCtx);
const idxStart=html.indexOf('function mvHybridNorm('),idxEnd=html.indexOf('async function mvHybridFetchCard(',idxStart);
assert(idxStart>0&&idxEnd>idxStart);
vm.runInContext('let mvNames=[],mvNamesReady=false;\n'+html.slice(idxStart,idxEnd),tokenCtx);
vm.runInContext("mvHybridIndex(['Sol Ring','Arbor Elf'])",tokenCtx);
assert.equal(vm.runInContext("mvHybridCandidates('Arbor EIf')[0]?.name",tokenCtx),'Arbor Elf',
 'one I/l OCR lookalike recovers Arbor Elf without an arbitrary fuzzy title guess');
assert.equal(vm.runInContext("mvHybridCandidates('JACE')[0]?.name",tokenCtx),'Jace',
 'missing token name is recovered from the bundled printing catalog');
assert.equal(vm.runInContext("mvHybridCandidates('Jace')[0]?.exact",tokenCtx),true);
assert.equal(hits,1,'exact recovered token name is cached');
console.log('v5.12.10: token-only name lookup and set-total-vs-collector safeguards passed.');
