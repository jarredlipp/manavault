/** Source-level integration checks plus deterministic live cadence telemetry. */
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const path=__dirname+'/../';
const native=fs.readFileSync(path+'app/src/main/java/com/manavault/app/NativeScannerActivity.java','utf8');
const main=fs.readFileSync(path+'app/src/main/java/com/manavault/app/MainActivity.java','utf8');
const html=fs.readFileSync(path+'app/src/main/assets/index.html','utf8');
const coordinator=fs.readFileSync(path+'app/src/main/java/com/manavault/app/ScanCycleCoordinator.java','utf8');
const matcher=fs.readFileSync(path+'app/src/main/java/com/manavault/app/VisualMatcher.java','utf8');
assert.match(native,/scanCycle\.observedNewScene\(now\)/);
assert.match(native,/scanCycle\.receivedFinalReceipt\(id,/);
assert.match(native,/if\(waitingForNext && scanCycle\.hasSceneReady\(\) && resolutionPending\)return;/);
assert.match(native,/!id\.equals\(scanCycle\.pendingId\(\)\)/);
assert.match(native,/frameInterval=reviewDialogOpen\?180:waitingForNext\?65:75/);
assert.doesNotMatch(native,/now-lastSavedAt\s*<\s*20000/);
assert.match(native,/confirmedNewTop=true/);
assert.match(native,/learnConfirmedPhotograph/);
assert.match(matcher,/synchronized\(this\)/);
assert.match(coordinator,/synchronized boolean observedNewScene/);
assert.match(coordinator,/synchronized boolean receivedFinalReceipt/);
assert.match(main,/return "5\.12\.24"/);
assert.match(html,/manual_selected:Boolean\(data\.manual_selected\)/);
assert.match(html,/capture_gap_ms/);
assert.match(html,/manual_review_ms/);
assert.match(html,/Scan cycle SLOW:/);
assert.match(html,/symbolReason/);
const start=html.indexOf('let mvLiveLastCaptureElapsed=null;');
const end=html.indexOf('let mvLiveLastCatalogState=',start);
assert.ok(start>=0&&end>start);
const out=[];const ctx={Number,Math,String,console,mvLogCamera:(msg)=>out.push(msg)};
vm.createContext(ctx);vm.runInContext(html.slice(start,end),ctx);
vm.runInContext('mvLiveCapturePerformance({elapsed:1000,capture_gap_ms:-1,name:"A"});'+
 'mvLiveCapturePerformance({elapsed:2200,capture_gap_ms:1200,name:"B"});'+
 'mvLiveCapturePerformance({elapsed:7600,capture_gap_ms:5400,name:"C"});'+
 'mvLivePerf.saved=3;mvLivePerf.settled=3;',ctx);
const summary=vm.runInContext('mvLivePerformanceSummary()',ctx);
assert.match(summary,/3 saved of 3 handled/);
assert.match(summary,/gaps >4s 1/);
assert.equal(out.length,1);
assert.match(out[0],/Scan cycle SLOW: 5400ms/);
console.log('v5.12.24 throughput integration and telemetry checks passed');
