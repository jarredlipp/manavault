/** Native copy-state source regression: explicit user intent, never an implied duplicate. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const source=fs.readFileSync(__dirname+'/../app/src/main/java/com/manavault/app/NativeScannerActivity.java','utf8');
assert.match(source,/"Same card · no save"/);
assert.match(source,/"Another copy · scan \+1"/);
assert.match(source,/"Same physical card · no additional copy saved"/);
assert.match(source,/if\(reason\.contains\("identical copy"\)\)/);
const part=source.slice(source.indexOf('if(reason.contains("identical copy"))'),source.indexOf('boolean receiptAlreadyArrived=scanCycle.observedNewScene(now)'));
assert(!/scanCycle\.observedNewScene/.test(part),'Identical-looking cover must not authorize a capture automatically');
assert.match(source,/sameCopyOverridePending=true/);
assert.match(source,/resolutionPending \|\| reviewDialogOpen/);
assert.match(source,/Long\.bitCount\(hash\^lastCapturedHash\)<=8/);
assert.match(source,/scanCycle\.rearmConfirmedPrevious/);
console.log('v5.12.25 copy controls: same-card no-save, explicit +1 copy, no implicit repeat passed');
