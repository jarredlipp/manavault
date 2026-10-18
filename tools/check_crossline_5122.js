const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const snippet=html.slice(html.indexOf('function mv61CollectorKey('),html.indexOf('function mv61NormalImage('));
const ctx={};vm.createContext(ctx);vm.runInContext(snippet,ctx);
function match(lines,printings){ctx.lines=lines;ctx.printings=printings;return vm.runInContext('mv122CrossLineFooterMatch(lines,printings)',ctx);}
const cards=[
 {id:'a',name:'Stillness in Motion',set:'tdm',collector_number:'334'},
 {id:'b',name:'Stillness in Motion',set:'ptdm',collector_number:'334'},
 {id:'c',name:'Stillness in Motion',set:'tdm',collector_number:'119'}
];
assert.equal(match([{zone:'footer-left',text:'R 0334'}, {zone:'footer-deep',text:'TDM EN'}],cards)?.printing.id,'a');
assert.equal(match([{zone:'footer-left',text:'R 0348'}, {zone:'footer-deep',text:'MKM EN'}],[
 {id:'m',name:'Fugitive Codebreaker',set:'mkm',collector_number:'348'},
 {id:'n',name:'Fugitive Codebreaker',set:'mkm',collector_number:'127'}])?.printing.id,'m');
assert.equal(match([{zone:'footer-left',text:'R 0334'},{zone:'footer-line',text:'TDM EN'},{zone:'footer-deep',text:'PTDM'}],cards),null,'conflicting set must not verify');
assert.equal(match([{zone:'footer-left',text:'R 0334'},{zone:'footer-line',text:'TDM EN'}],[cards[0],{...cards[0],id:'variant'}]),null,'colliding printings must not verify');
assert.equal(match([{zone:'footer-left',text:'©2025 Wizards'},{zone:'footer-line',text:'TDM EN'}],cards),null);
console.log('Cross-line footer evidence: distinct lines, ambiguity, conflicting codes, year safeguards passed.');
