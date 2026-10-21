/** Regression gates for rules-text OCR and explicit verification consent. */
const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const start=html.indexOf('function mv613NonTitleText(');
const end=html.indexOf('// Prefer a set code printed beside',start);
assert(start>=0 && end>start);
const ctx={mvHybridNorm:s=>String(s||'').toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim()};
vm.createContext(ctx);vm.runInContext(html.slice(start,end),ctx);
for(const bad of ['Cycling','Each other creature you control with a','Haste','Whenever this creature attacks','Of the Coast','but as long as injustice exists, I\'m still needed'])
 assert.equal(ctx.mv613NonTitleText(bad),true,`${bad} must be non-title`);
for(const good of ['Exava, Rakdos Blood Witch','Cataclysmic Gearhulk','Web-Slinger Elite','Tigereye Cameo','Teysa, Envoy of Ghosts','Captain America, Living Legend','Land Tax','Artifact Mutation'])
 assert.equal(ctx.mv613NonTitleText(good),false,`${good} must remain eligible`);
assert(!html.includes("message='Name verified; catalog still preparing (edition unverified)'"));
assert(!html.includes("message='Name saved safely; printing deferred after lookup failure'"));
const native=fs.readFileSync('app/src/main/java/com/manavault/app/NativeScannerActivity.java','utf8');
assert(native.includes('unresolvedRetryCount>=2'));
assert(native.includes('selectTitle(task.getResult(),strip.getHeight(),names)'));
console.log('Recognition reliability gates passed');
