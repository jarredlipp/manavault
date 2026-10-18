/** Universal physical-printing-mark regression fixtures. No network required. */
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const from=html.indexOf('function mv61CollectorKey('),to=html.indexOf('function mv61NormalImage(');
assert.ok(from>0&&to>from,'footer parser not found');
const ctx={};vm.createContext(ctx);vm.runInContext(html.slice(from,to),ctx);
const decide=(lines,printings)=>{ctx.lines=lines;ctx.printings=printings;return vm.runInContext('mv126ResolveStructuredFooter(lines,printings)',ctx)};
const row=(text,zone='footer-left',pass='color')=>({zone,text,pass});
const defile=[
 {id:'mh1-86',name:'Defile',set:'mh1',collector_number:'86',rarity:'common'},
 {id:'mh1-191',name:'Defile',set:'mh1',collector_number:'191',rarity:'common'},
 {id:'mh2-254',name:'Defile',set:'mh2',collector_number:'254',rarity:'common'},
 {id:'pmh1-86',name:'Defile',set:'pmh1',collector_number:'86',rarity:'common'}
];
let result=decide([row('086/254 C'),row('MH1 • EN','footer-line')],defile);
assert.equal(result.printing?.id,'mh1-86','086/254 should mean collector 86, not 254');
assert.equal(result.evidence.setTotals[0],'254');
assert.equal(result.evidence.collector,'86');
assert.equal(result.evidence.language[0],'EN');
result=decide([row('086/254 C'),row('254','footer-deep'),row('MH1 EN','footer-line')],defile);
assert.equal(result.printing?.id,'mh1-86','a second pass reading only 254 is not contradictory');
result=decide([row('086/254 C'),row('MH1 EN','footer-line'),row('MH2 EN','footer-deep')],defile);
assert.equal(result.printing,undefined,'two clearly observed sets are contradictory');
assert.equal(result.conflict,true);
result=decide([row('086/254 C'),row('MH1 EN','footer-line')],[defile[0],{...defile[0],id:'treatment-variant'}]);
assert.equal(result.printing,undefined,'multiple catalog variants cannot be guessed');
result=decide([row('086/254 C'),row('MH1 EN','footer-line'),row('R 0191','footer-deep')],defile);
assert.equal(result.printing,undefined,'a different valid collector number in the same set is contradictory');
result=decide([row('086/254 C'),row('MH1 EN','footer-line')],[{...defile[0],rarity:'rare'}]);
assert.equal(result.printing,undefined,'conflicting observed rarity vetoes automatic verification');
result=decide([row('©2019 Wizards of the Coast Inc'),row('MH1 EN','footer-line')],defile);
assert.equal(result.printing,undefined,'copyright year cannot be a collector number');
result=decide([row('R 0345'),row('DFT EN','footer-line')],[
 {id:'web-345',set:'dft',name:'Webstrike Elite',collector_number:'345',rarity:'rare'},
 {id:'web-25',set:'dft',name:'Webstrike Elite',collector_number:'25',rarity:'rare'}]);
assert.equal(result.printing?.id,'web-345','alternate font rarity + collector must work');
result=decide([row('R 0334'),row('TDM EN','footer-line')],[
 {id:'tdm-334',set:'tdm',name:'Stillness in Motion',collector_number:'334'},
 {id:'tdm-12',set:'tdm',name:'Stillness in Motion',collector_number:'12'}]);
assert.equal(result.printing?.id,'tdm-334','cross-line set + collector match');
result=decide([row('R 0348'),row('MKM EN','footer-line')],[
 {id:'mkm-348',set:'mkm',collector_number:'348',name:'Fugitive Codebreaker'},
 {id:'mkm-127',set:'mkm',collector_number:'127',name:'Fugitive Codebreaker'}]);
assert.equal(result.printing?.id,'mkm-348','set-first selection for showcase card');
result=decide([row('71/248'),row('© 2010 Wizards of the Coast','footer-deep')],[
 {id:'roe-71',set:'roe',collector_number:'71',name:'Hada Spy Patrol'},
 {id:'cma-40',set:'cma',collector_number:'40',name:'Hada Spy Patrol'}]);
assert.equal(result.printing?.id,'roe-71','unambiguous numbered older card');
result=decide([row('Odissey • EN','footer-left'),row('No number','footer-line')],[
 {id:'old-a',set:'ody',collector_number:'3',name:'Example'},
 {id:'old-b',set:'cma',collector_number:'3',name:'Example'}]);
assert.equal(result.printing,undefined,'unreadable printed code cannot imply an edition');
result=decide([row('ODY EN')],[{id:'only-ody',set:'ody',collector_number:'17',name:'Example'}]);
assert.equal(result.printing?.id,'only-ody','trust unique set-labeled printing with no collector');
result=decide([row('ODY EN')],[
 {id:'only-ody',set:'ody',collector_number:'17',name:'Example'},
 {id:'alt-ody',set:'ody',collector_number:'20',name:'Example'}]);
assert.equal(result.printing,undefined,'same-set printings still ambiguous');
result=decide([row('C 0089'),row('EVE EN','footer-line')],[
 {id:'eve-89',set:'eve',collector_number:'89',name:'Bloodied Ghost',rarity:'common'},
 {id:'plst',set:'plst',collector_number:'EVE-83',name:'Bloodied Ghost'}]);
assert.equal(result.printing?.id,'eve-89');
console.log('Universal structured footer: fraction/total, set-first, rarity, conflicting variants, older frames, and cross-line tests passed.');
