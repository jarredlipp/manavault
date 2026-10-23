/** Exact-name, legacy collector + weak fragments regression fixtures. */
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const from=html.indexOf('function mv61CollectorKey('),to=html.indexOf('function mv61NormalImage(');
assert(from>0&&to>from);
const ctx={};vm.createContext(ctx);vm.runInContext(html.slice(from,to),ctx);
function decide(lines,prints,exact=true){
 ctx.lines=lines.map(t=>({zone:t[1]||'footer-left',text:t[0],pass:t[2]||'color'}));
 ctx.prints=prints;ctx.exact=exact;
 return vm.runInContext('mv126ResolveStructuredFooter(lines,prints,{titleVerified:exact})',ctx);
}
const cards=[
 ['rtr221','rtr','221'],['other3','other','3'],
 ['promo221','prtr','221'],['alt222','alt','222']
].map(([id,set,collector_number])=>({id,set,collector_number,name:'Rakdos Shred-Freak'}));
assert.equal(decide([['221'],['3','footer-deep']],cards.slice(0,2)).printing?.id,'rtr221');
assert.equal(decide([['254'],['25','footer-deep']],[
 {id:'7ed254',set:'7ed',collector_number:'254'},
 {id:'other25',set:'other',collector_number:'25'}
]).printing?.id,'7ed254');
assert.equal(decide([['221']],cards.slice(0,3)).printing,undefined,
 'two printings share collector 221: refuse to choose an edition');
assert.equal(decide([['221'],['222','footer-deep']], [cards[0],cards[3]]).printing,undefined,
 'two credible full-length collector numbers are conflicting');
assert.equal(decide([['221'],['3','footer-deep']],cards.slice(0,2),false).printing,undefined,
 'an unsupported OCR candidate is not a verified card name');
assert.equal(decide([['221/274'],['3','footer-deep']],cards.slice(0,2)).printing?.id,'rtr221');
console.log('Legacy collector fusion: strong printed number, weak noise, shared variants, conflict and title gate passed');
