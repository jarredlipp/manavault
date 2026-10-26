'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(root,'app/src/main/assets/index.html'),'utf8');
const java=fs.readFileSync(path.join(root,'app/src/main/java/com/manavault/app/NativeScannerActivity.java'),'utf8');
function section(start,end){const a=html.indexOf(start);assert(a>=0,'missing '+start);const b=html.indexOf(end,a+start.length);assert(b>a,'missing end '+end);return html.slice(a,b);}
const near=section('// v5.13.3: A known set','// A trustworthy illustrator');
const ctxt=vm.createContext({Map,Set,Array,Number,String,console});
vm.runInContext(`function mv61CollectorKey(v){const m=String(v||'').trim().toLowerCase().match(/^([a-z]?)(\\d{1,5})([a-z]?)$/);return m?m[1]+String(Number(m[2]))+m[3]:'';}\n${near}`,ctxt);
const hint=ctxt.mv133CollectorNearMissReview;
const printings=[{id:'a',set:'m21',collector_number:'248',name:'Radiant Fountain'},
 {id:'b',set:'m21',collector_number:'156',name:'Radiant Fountain'},
 {id:'c',set:'m15',collector_number:'248',name:'Radiant Fountain'}];
const evidence=(nums,sets=['m21'],fractions=[])=>({sets:new Map(sets.map(s=>[s,{score:3}])),numbers:new Map(nums.map(n=>[n,{score:5}])),fractions});
const r=hint([],printings,evidence(['24b']));
assert(r && r.printings[0].id==='a' && r.printings.length===3,'M21 #24b must be a review-only suggestion');
assert(!r.printing,'OCR glyph conversion MUST NOT return auto-save printing');
assert.strictEqual(hint([],printings,evidence(['248'])),null,'exact number is not a near miss');
assert.strictEqual(hint([],printings,evidence(['24b'],['m21','m15'])),null,'conflicting sets block suggestion');
assert.strictEqual(hint([],printings,evidence(['24b'],['m21'],[{collector:'248'}])),null,'an observed fraction blocks guess');
assert.strictEqual(hint([],printings.concat({id:'d',set:'m21',collector_number:'248',name:'Radiant Fountain'}),evidence(['24b'])),null,'multiple possible near misses block suggestion');
const logs=[];
const sandbox=vm.createContext({localStorage:{_data:{},getItem(k){return this._data[k]||null},setItem(k,v){this._data[k]=v}},
 console,Map,Set,Array,Number,String,Date,document:{getElementById:()=>null},navigator:{},window:{ManaVaultNative:{shareLog:s=>logs.push(s)}},
 esc:s=>String(s),mv58NativeStatus:s=>logs.push(s),mvLogCamera:s=>logs.push(s)});
const aud=section('// v5.13.3: A PHYSICAL accuracy audit.','const mvLiveQueue=[];');
vm.runInContext(aud+'\nglobalThis.auditApi={add:mv133RecordAudit,mark:mv133MarkAudit,export:mv133ExportAudit,items:mv133ScanAudit};',sandbox);
const api=sandbox.auditApi;
api.add({scan_id:'s-1',name:'No More Lies',printing:{id:'uuid',set:'mkm',collector_number:'221'},manual_selected:false,verification_ms:249,manual_review_ms:0},'verified','structured set + collector numerator');
api.add({scan_id:'s-1',name:'No More Lies',printing:{id:'uuid',set:'mkm',collector_number:'221'}},'verified','duplicate receipt');
assert.strictEqual(api.items.length,1,'audit receipt must be idempotent');
api.mark('s-1','incorrect');api.export();
assert.strictEqual(api.items[0].judgment,'incorrect');
assert(logs[0].includes('printingId\tset\tcollector')&&logs[0].includes('mkm\t221'),'export must include exact printing details');
assert(!logs[0].includes('image_path'),'photos must not be exported');
const clock=section('const mvLivePerf={','let mvLiveLastCatalogState=');
vm.runInContext(clock+'\nglobalThis.perfApi={record:mvLiveCapturePerformance,summary:mvLivePerformanceSummary,perf:mvLivePerf};',
 vm.createContext(Object.assign(sandbox,{mvLiveLastCaptureElapsed:null})));
const perf=sandbox.perfApi;
perf.record({capture_gap_ms:6000,scene_to_capture_ms:200,previous_receipt_latency_ms:5100,previous_receipt_to_capture_ms:300,name:'Card 1'});
perf.record({capture_gap_ms:6000,scene_to_capture_ms:4100,previous_receipt_latency_ms:100,previous_receipt_to_capture_ms:5600,name:'Card 2'});
perf.record({capture_gap_ms:6000,scene_to_capture_ms:100,previous_receipt_latency_ms:100,previous_receipt_to_capture_ms:5600,name:'Card 3'});
assert.strictEqual(perf.perf.slowPreviousReview,1);
assert.strictEqual(perf.perf.slowSceneToCapture,1);
assert.strictEqual(perf.perf.slowBeforeScene,1);
assert(java.includes('previous_receipt_latency_ms')&&java.includes('previous_receipt_to_capture_ms'));
assert(html.includes('mv133RecordAudit(receipt,result,message)'),'audit must receive committed receipts');
assert(html.includes('mv133CollectorNearMissReview(marks.lines,print.printings'),'near miss should run only after hard structured conflict');
assert(html.includes('mv133ExportAudit')&&html.includes('mv133ScanAudit'),'audit UI must be available');
console.log('v5.13.3 audit, review-only footer rescue and slow-gap diagnostics PASS');
