/** Regression: October 21 physical scanner logs. Execute with node. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv61CollectorKey('),end=html.indexOf('function mv61NormalImage(',start);
assert(start>0&&end>start);
const sandbox={};vm.createContext(sandbox);vm.runInContext(html.slice(start,end),sandbox);
const row=(text,zone='footer-left')=>({text,zone,pass:'color'});
function resolve(lines,printings){sandbox.lines=lines;sandbox.printings=printings;return vm.runInContext('mv126ResolveStructuredFooter(lines,printings)',sandbox)}
const h=(set,num,id=set+num)=>({id,name:'Abyssal Harvester',set,collector_number:String(num),rarity:'rare'});
const printings=[h('fdn',54),h('fdn',202),h('dsk',54)];
for(const text of ['O054','RO054','LO054','IO054','R 0054']){
 const actual=resolve([row(text),row('FDN EN','footer-line')],printings);
 assert.equal(actual.printing?.id,'fdn54',`${text} should recover 54 scoped to the observed FDN set`);
}
for(const [mark,number,rarity] of [['CO100','100','common'],['U0232','232','uncommon'],['L0273','273','rare'],['RO147','147','rare']]){
 const candidates=[{...h('ecl',number),rarity},{...h('fdn',number),rarity}];
 const actual=resolve([row(mark),row('ECL EN','footer-line')],candidates);
 assert.equal(actual.printing?.id,'ecl'+number,mark+' should recover a catalog-scoped number');
}
let result=resolve([row('O054'),row('R 0202','footer-deep'),row('FDN EN','footer-line')],printings);
assert.equal(result.printing,undefined,'genuine strong conflicting candidate collectors require manual review');
result=resolve([row('O054'),row('FDN EN','footer-line')],[h('fdn',54,'base'),h('fdn',54,'showcase')]);
assert.equal(result.printing,undefined,'sharing set and number across treatments cannot auto-select');
result=resolve([row('O054','type-bar'),row('FDN EN','footer-line')],printings);
assert.equal(result.printing,undefined,'type-bar OCR must never supply a collector number');
result=resolve([row('ILLUS O054 JASON CHAN'),row('FDN EN','footer-line')],printings);
assert.equal(result.printing,undefined,'artist credit must not become collector evidence');
result=resolve([row('© 2024 Wizards of the Coast'),row('FDN EN','footer-line')],printings);
assert.equal(result.printing,undefined,'copyright text must not become collector evidence');
result=resolve([row('O054'),row('DSK EN','footer-line')],printings);
assert.equal(result.printing?.id,'dsk54','set code always governs collector scope');
console.log('v5.12.19 collector OCR rescue cases passed');
