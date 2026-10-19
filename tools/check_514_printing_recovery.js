/** Targeted regressions derived from the phone logs: October 19. */
const assert=require('node:assert/strict'), fs=require('fs'), vm=require('vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const first=html.indexOf('function mv61CollectorKey('),last=html.indexOf('function mv61NormalImage(');
assert(first>0&&last>first);
const ctx={};vm.createContext(ctx);vm.runInContext(html.slice(first,last),ctx);
function evaluate(code,lines,printings){Object.assign(ctx,{lines,printings});return vm.runInContext(code+'(lines,printings)',ctx)}
const row=(text,zone='footer-left',pass='color')=>({text,zone,pass});
const same=(name,code,collector,artist,id)=>({id:id||code+'-'+collector,name,set:code,collector_number:collector,artist,rarity:'common'});
// Joined language code: 'EMNEN' and 'SOIEN' must be parsed as printed set codes
const cathar=[same('Steadfast Cathar','emn','44','Dan Scott'),same('Steadfast Cathar','som','17','Dan Scott')];
assert.equal(evaluate('mv126ResolveStructuredFooter',[row('C 044'),row('EMNEN','footer-line')],cathar).printing?.id,'emn-44');
assert.equal(evaluate('mv61SetHints',[row('SOIEN')],[same('Catalog','soi','51','Voss')])[0],'soi');
assert.equal(evaluate('mv126ResolveStructuredFooter',[row('SOIEN')],[same('Catalog','soi','51','Voss')]).printing?.id,'soi-51');
// Fragment inside an illustrator's longer word cannot turn into a set code
assert.equal(evaluate('mv61SetHints',[row('EMNEMAN')],cathar).length,0);
// Illustrator + independently repeated collector allows a unique older printing
const lantern=[same('Soul-Guide Lantern','thb','237','Cliff Childs'),same('Soul-Guide Lantern','brc','54','Cliff Childs'),same('Soul-Guide Lantern','moc','289','Lars Grant-West')];
let lines=[row('054','footer-left','color'),row('54','footer-line','contrast'),row('Illus. Cliff Childs','footer-deep')];
assert.equal(evaluate('mv614ArtistCollectorMatch',lines,lantern).printing?.id,'brc-54');
assert.equal(evaluate('mv614ArtistCollectorMatch',[row('054'),row('54','footer-line','contrast')],lantern),null,
 'collector without independent illustrator cannot force a printing');
assert.equal(evaluate('mv614ArtistCollectorMatch',[row('054'),row('54','footer-line','contrast'),row('Cliff')],lantern),null,
 'an artist first name alone cannot verify');
assert.equal(evaluate('mv614ArtistCollectorMatch',lines,[...lantern,{...lantern[1],id:'brc-54-variant'}]),null,
 'same number and artist with a second treatment remains ambiguous');
assert.equal(evaluate('mv614ArtistCollectorMatch',[row('R 009/254'),...lines],lantern),null,
 'contradictory complete fraction vetoes the illustrator rescue');
// The review may prioritize candidates, but never suppress another edition.
const ordered=evaluate('mv614ReviewOrder',[row('54'),row('Illus. Cliff Childs')],lantern);
assert.equal(ordered.length,lantern.length);
assert.equal(ordered[0].id,'brc-54');
assert(ordered.some(p=>p.id==='moc-289'),'unfavored options remain visible');
// Missing symbols for older core sets do not imply an automatic match
assert.equal(evaluate('mv614ArtistCollectorMatch',[row('© 1993 Wizards of the Coast')],
 [same('Living Lands','4ed','178','Charles Gillespie'),same('Living Lands','5ed','162','Charles Gillespie')]),null);
console.log('v5.12.14 printing rescue: joined footer language, illustrator/collector corroboration and collision guards passed.');
