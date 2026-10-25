/** v5.13.1: Rules-text matches may not override clear, conflicting title words. */
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv131ReferenceTitleCompatible(');
const end=html.indexOf('// v5.13.0: A fuzzy name',start);
assert(start>0 && end>start);
const context={mvHybridNorm:x=>String(x||'').toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim()};
vm.createContext(context);vm.runInContext(html.slice(start,end),context);
const agrees=context.mv131ReferenceTitleCompatible;
assert.equal(agrees('Captain Storm, migo Raider O','The Borg Queen'),false);
assert.equal(agrees('Exava, Rakdos Blood Witch','Halana and Alena, Partners'),false);
assert.equal(agrees('Raggadragga, Goreguts Boss 200','Raggadragga, Goreguts Boss'),true);
assert.equal(agrees('O Depraved Harvester','Desperate Farmer // Depraved Harvester'),true);
assert.equal(agrees('Unrecognized title','Aisha of Sparks and Smoke'),false);
assert.equal(agrees('Who // What // When // Where // Why','Webstrike Elite'),true);
assert.match(html,/if\(proposed && mvHybridNorm\(proposed\)!==mvHybridNorm\(observed\) &&\s*mv131ReferenceTitleCompatible/);
console.log('Rules-text suggestion conflict gate PASS');
