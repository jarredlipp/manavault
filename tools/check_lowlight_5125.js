const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const root=__dirname+'/../';
const src=fs.readFileSync(root+'app/src/main/assets/index.html','utf8');
const from=src.indexOf('function mv61CollectorKey('),to=src.indexOf('function mv61NormalImage(');
assert.ok(from>0&&to>from,'reference functions must be present');
const ctx={};vm.createContext(ctx);vm.runInContext(src.slice(from,to),ctx);
function verify(lines,printings){ctx.lines=lines;ctx.printings=printings;return vm.runInContext('mv125SetScopedFooterMatch(lines,printings)',ctx);}
const variants=[
 {id:'mh1',name:'Defile',set:'mh1',collector_number:'86'},
 {id:'mh2',name:'Defile',set:'mh2',collector_number:'254'},
 {id:'p',name:'Defile',set:'pmh1',collector_number:'86'},
];
assert.equal(verify([{zone:'footer-left',text:'086/254 C'}, {zone:'footer-line',text:'MH1 EN'}],variants)?.printing.id,'mh1', 'set total 254 must not override physical #086');
assert.equal(verify([{zone:'footer-left',text:'086/254 C'},{zone:'footer-line',text:'MH1 EN'},{zone:'footer-deep',text:'MH2 EN'}],variants),null, 'multiple observed sets must prevent automatic assignment');
assert.equal(verify([{zone:'footer-left',text:'086/254 C'},{zone:'footer-line',text:'MH1 EN'}],[variants[0],{...variants[0],id:'variant'}]),null,'duplicate editions must stay in review');
const native=fs.readFileSync(root+'app/src/main/java/com/manavault/app/NativeScannerActivity.java','utf8');
const edge=fs.readFileSync(root+'app/src/main/java/com/manavault/app/CardQuadDetector.java','utf8');
for(const check of [/LowLightPolicy\.next/,/setExposureCompensationIndex/,/focusMetered/,/LOW_LIGHT_PREF/,/frameInterval=reviewDialogOpen\?180:waitingForNext\?65:75/])assert.match(native,check);
assert.match(edge,/mean<82\?28:45/);
assert.match(edge,/luma<82\?2\.7:2\.2/);
console.log('Low-light controls and MH1/086 set-scoped printing resolution: PASS');
