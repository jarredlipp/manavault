const assert = require('node:assert/strict');
const fs=require('node:fs');
const main=fs.readFileSync(__dirname+'/../app/src/main/java/com/manavault/app/MainActivity.java','utf8');
for(const method of ['readPrintingMarks','readReferenceText']){
 const start=main.indexOf('public void '+method+'(');
 const next=main.indexOf('@JavascriptInterface',start+1);
 assert(start>=0 && next>start);
 const body=main.slice(start,next);
 assert.match(body,/printingEvidenceExecutor\.execute\(/);
 assert.doesNotMatch(body,/artworkExecutor\.execute\(/);
}
for(const method of ['rankPrintings','checkArtwork']){
 const start=main.indexOf('public void '+method+'(');
 let next=main.indexOf('@JavascriptInterface',start+1);
 if(next<0)next=main.indexOf('}, "ManaVaultNativeScanner")',start);
 assert(start>=0 && next>start);
 assert.match(main.slice(start,next),/artworkExecutor\.execute\(/);
}
assert.match(main,/printingEvidenceExecutor\.shutdownNow\(\)/);
console.log('Footer OCR runs independently of slow artwork requests.');
