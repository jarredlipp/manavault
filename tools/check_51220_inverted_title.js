/** Regression: flavor attribution / artist name cannot be automatically saved as a catalog-exact title from inverted crop. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/index.html','utf8');
const start=source.indexOf('function mv133FooterFirstRequired(');
const end=source.indexOf('// v5.12.11: CameraX sends each captured image',start);
assert(start>=0&&end>start,'decision function available');
const sandbox={};vm.createContext(sandbox);vm.runInContext(source.slice(start,end),sandbox);
(async()=>{
 const bad=await sandbox.mv60DecideCapture({name:'Volrath',source:'ocr-exact',photo_rotation_degrees:180},0,{liveFast:true});
 assert.equal(bad.printing,undefined);
 assert.match(bad.reason,/inverted portrait/);
 const good=await sandbox.mv60DecideCapture({name:'Volrath',source:'ocr-exact',photo_rotation_degrees:-180},0,{liveFast:true});
 assert.equal(good.printing,undefined);
 assert.match(good.reason,/inverted portrait/);
 console.log('v5.12.20 inverted portrait/flavor title safety passed');
})().catch(e=>{console.error(e);process.exitCode=1});
