// Regression tests for collectors that are legible on the physical card.
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const norm=s=>String(s||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
const snippet=html.slice(html.indexOf('function mv61CollectorKey('),html.indexOf('function mv61NormalImage('));
const context={};vm.createContext(context);vm.runInContext(snippet,context);
const check=(name,lines,printings)=>{
 context.lines=lines;context.printings=printings;
 return vm.runInContext('mv121UniqueCollectorPrinting(lines, printings)',context);
};
const editions=[
 {id:'anthology',name:'Hada Spy Patrol',set:'cma',collector_number:'40'},
 {id:'commander',name:'Hada Spy Patrol',set:'c13',collector_number:'46'},
 {id:'rise',name:'Hada Spy Patrol',set:'roe',collector_number:'71'},
 {id:'list',name:'Hada Spy Patrol',set:'plst',collector_number:'CMA-40'}
];
const oldCard=[{zone:'footer-left',pass:'color',text:'C13 046'},
 {zone:'footer-deep',pass:'contrast',text:'046'}];
assert.equal(check('Hada',oldCard,editions)?.printing.id,'commander');
assert.equal(check('Hada',[oldCard[0]],editions),null,'one reading alone must not verify');
assert.equal(check('Hada',[...oldCard,{zone:'footer-line',pass:'color',text:'071'}],editions),null,'contradictory second collector number');
assert.equal(check('Hada',[...oldCard,{zone:'footer-line',pass:'color',text:'ROE 071'}],editions),null,'conflicting printed set code');
const other=[{id:'a',name:'Made Up',set:'foo',collector_number:'46'},
 {id:'b',name:'Made Up',set:'bar',collector_number:'46'}];
assert.equal(check('dup',oldCard,other),null,'same collector number in two sets must not auto-save');
assert.equal(check('copyright',[{zone:'footer-left',pass:'color',text:'© 2013 Wizards of the Coast'},
 {zone:'footer-deep',pass:'contrast',text:'© 2013 Wizards of the Coast'}],editions),null);
const native=fs.readFileSync(__dirname+'/../app/src/main/java/com/manavault/app/PrintingFingerprintVerifier.java','utf8');
assert.match(native,/"footer-deep"/);
assert.match(native,/\.830f,\.72f,\.162f/);
assert.match(html,/const uniqueCollector=mv121UniqueCollectorPrinting\(marks.lines,print.printings\)/);
console.log('Collector-first identification tests passed: unique number, conflicting marks, shared numbers, copyright guard and bottom-edge rescans.');
