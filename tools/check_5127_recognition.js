/** Targeted v5.12.7 collector normalization and safety regression. */
const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv61CollectorKey('),end=html.indexOf('function mv61NormalImage(');
assert.ok(start>=0&&end>start);
const ctx={};vm.createContext(ctx);vm.runInContext(html.slice(start,end),ctx);
function resolve(lines,cards){ctx.lines=lines;ctx.cards=cards;return vm.runInContext('mv126ResolveStructuredFooter(lines,cards)',ctx);}
const line=(text,zone='footer-left')=>({text,zone,pass:'color'});
const dft=[{id:'dft345',name:'Webstrike Elite',set:'dft',collector_number:'345',rarity:'rare'},
 {id:'dft20',name:'Webstrike Elite',set:'dft',collector_number:'20',rarity:'common'}];
for(const raw of ['RO345','R0345','R 0345','0345']){
 const r=resolve([line(raw),line('DFT EN','footer-line')],dft);
 assert.equal(r.printing?.id,'dft345',raw+' should resolve to printing 345');
}
assert.equal(resolve([line('RO345'),line('DFT EN','footer-line'),line('R 0020','footer-deep')],dft).printing,undefined,
 'contradictory well-formed collector numbers must still require review');
assert.equal(resolve([line('©2025 Wizards of the Coast'),line('DFT EN','footer-line')],dft).printing,undefined,
 'copyright text is never collector evidence');
console.log('v5.12.7 structured OCR: RO345, R0345, R 0345, conflict and copyright cases passed.');
