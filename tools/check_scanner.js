/** Run with `node tools/check_scanner.js` before Android compilation. */
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
for (const [i, script] of [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)].entries()) {
  new vm.Script(script[1],{filename:`index-script-${i+1}.js`});
}
const files=[
  'app/src/main/java/com/manavault/app/MainActivity.java',
  'app/src/main/java/com/manavault/app/NativeScannerActivity.java',
  'app/src/main/java/com/manavault/app/CardEdgeOverlay.java',
  'app/src/main/java/com/manavault/app/CardQuadDetector.java',
  'app/src/main/java/com/manavault/app/ArtworkVerifier.java',
  'app/src/main/java/com/manavault/app/VisualMatcher.java',
  'app/src/main/AndroidManifest.xml',
  '.github/workflows/build-apk.yml',
  'README.md'
];
for (const file of files) assert.ok(fs.existsSync(path.join(root,file)),`Missing: ${file}`);
assert.deepEqual(fs.readdirSync(root).filter(x=>/^readme/i.test(x)),['README.md']);
const from=html.indexOf('function mvHybridNorm('),to=html.indexOf('async function mvHybridFetchCard(');
assert.ok(from>=0&&to>from,'Missing name matching functions');
let script=html.slice(from,to).replace(
 'let mvHybridBuckets=new Map(),mvHybridExact=new Map(),mvUnknownNameTried=new Set();',
 'mvHybridBuckets=new Map();mvHybridExact=new Map();let mvUnknownNameTried=new Set();');
const test=new Function('let mvNames=[],mvNamesReady=false;let mvHybridBuckets,mvHybridExact;const mvLogCamera=()=>{};'+script+`
 mvHybridIndex(['Fall','Jace','Tuinvale Treefolk // Oaken Boon','Webstrike Elite','Prismite','Read the Bones','New Horizons']);
 return ['Fall','Jace','Tuinvale Treefolk','Oaken Boon','WEVSTRIKE Ri.','RRRRRRRRRRRR','LIASY'].map(mvHybridCandidates);`);
const results=test();
assert.equal(results[0][0]?.name,'Fall');
assert.equal(results[1][0]?.name,'Jace');
assert.equal(results[2][0]?.name,'Tuinvale Treefolk // Oaken Boon');
assert.equal(results[3][0]?.name,'Tuinvale Treefolk // Oaken Boon');
assert.equal(results[4][0]?.name,'Webstrike Elite');
assert.equal(results[5].length,0);
assert.equal(results[6].length,0);
assert.match(html,/mvNativeScanBatch/, 'Missing native batch review callback');
assert.match(html,/mv59AfterReviewChoice/, 'Missing scan queue completion handler');
assert.match(html,/learnArtwork\(/, 'Missing confirmed-printing reference cache');
const native=fs.readFileSync(path.join(root,'app/src/main/java/com/manavault/app/NativeScannerActivity.java'),'utf8');
assert.match(native,/VisualMatcher\.Match/, 'Missing artwork-first native matching');
assert.match(native,/waitingForNext/, 'Missing card-change suppression');
assert.match(native,/scan_results/, 'Missing batch result');
console.log('ManaVault v5.9 checks passed: JS syntax, repository layout, recognition and queue wiring, 7 OCR name cases.');

const mainActivity=fs.readFileSync(path.join(root,'app/src/main/java/com/manavault/app/MainActivity.java'),'utf8');
assert.match(mainActivity,/Open native scanner/, 'Missing always-visible Android-native launch button');
assert.match(mainActivity,/launchNativeScanner\(\)/, 'Missing native Android launch handler');
assert.match(html,/id="mv591NativeShortcut"/, 'Missing shortcut beside legacy camera');
assert.match(html,/window\.mvNativeLaunchNow/, 'Missing native button bridge');
assert.match(html,/v5\.9\.2/, 'Missing visible v5.9.2 build marker');
console.log('ManaVault v5.9.2 native entry checks passed.');

// A syntax-only test missed a production crash: const info was reassigned by
// the v5.3 pricing enhancement, aborting the native scan-review setup.
assert.match(html,/\blet info=c=>/, 'info must be reassignable for pricing');
assert.doesNotMatch(html,/\bconst info=c=>/, 'Fatal const reassignment regression');
const indexFile=path.join(root,'app/src/main/assets/visual-index.json');
assert.ok(fs.existsSync(indexFile),'Artwork index asset must exist (even when empty)');
const visualIndex=JSON.parse(fs.readFileSync(indexFile,'utf8'));
assert.equal(visualIndex.format,'manavault-visual-v1');
assert.ok(Array.isArray(visualIndex.cards));
for (const card of visualIndex.cards){
  assert.equal(card.f.length,192,'Incorrect artwork fingerprint length');
}
assert.match(fs.readFileSync(path.join(root,'.github/workflows/build-apk.yml'),'utf8'),/build_seed_visual_index.py/);
console.log('Pricing/runtime regression and artwork-index bundling checks passed ('+visualIndex.cards.length+' references).');

// Also execute the complete inline application script with a minimal offline DOM.
// This catches runtime const-reassignment mistakes missed by syntax checks.
require('./check_runtime.js');
