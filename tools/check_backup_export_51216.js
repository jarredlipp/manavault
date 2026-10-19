/* Test the actual UI export path without an Android runtime. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const start=html.indexOf('let mvBackupPending = false');
const end=html.indexOf('function bind(){',start);
assert(start>=0&&end>start);
const script=html.slice(start,end);
function fixture(data,withBridge=true){
 let captured,call=0;
 const fields={exportBtn:{disabled:false},export2:{disabled:false},mvBackupStatus:{style:{}},mvBackupSettingsStatus:{style:{}}};
 const context={window:{},document:{getElementById:id=>fields[id]||null,body:{appendChild(){}}},STORE:'manavault-v1',
  localStorage:{getItem:()=>data},Blob,Date,JSON,Error,Array,String,URL:{createObjectURL:()=> 'blob:sample',revokeObjectURL:()=>{}},setTimeout() {}};
 if(withBridge)context.window.ManaVaultNative={exportBackup:(json,name)=>{captured={json,name};call++}};
 vm.createContext(context);vm.runInContext(script,context);
 return {context,fields,get call(){return call},get captured(){return captured}};
}
const original=JSON.stringify({cards:[{name:'Soul-Guide Lantern',qty:1,foil:true,collector_number:'237'}],decks:[{name:'test deck',cards:[]}],wishlist:[]});
let test=fixture(original);
vm.runInContext('backup()',test.context);
assert.equal(test.call,1);assert.equal(test.captured.json,original);assert.match(test.captured.name,/\.json$/);
assert.equal(test.fields.exportBtn.disabled,true);
vm.runInContext('backup()',test.context);assert.equal(test.call,1,'Double-click must not start another picker');
test.context.window.mvBackupExportResult({ok:false,message:'Cancelled'});
assert.equal(test.fields.exportBtn.disabled,false);
assert.match(test.fields.mvBackupStatus.textContent,/Cancelled/);
vm.runInContext('backup()',test.context);assert.equal(test.call,2,'Cancelled save must allow retry');
test.context.window.mvBackupExportResult({ok:true,message:'Backup saved and verified'});
assert.match(test.fields.mvBackupStatus.textContent,/saved and verified/);
test=fixture(null);vm.runInContext('backup()',test.context);
assert.equal(test.call,0);assert.match(test.fields.mvBackupStatus.textContent,/failed/i);
test=fixture('{"cards":"missing"}');vm.runInContext('backup()',test.context);
assert.equal(test.call,0);assert.match(test.fields.mvBackupStatus.textContent,/invalid/i);
const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/manavault/app/MainActivity.java'),'utf8');
assert.match(native,/Intent\.ACTION_CREATE_DOCUMENT/);
assert.match(native,/writeAndVerify\(payload/);
assert.match(native,/@JavascriptInterface public void exportBackup\(/);
assert.match(native,/BACKUP_DOCUMENT_REQUEST/);
assert.match(html,/if\(localStorage\.getItem\(STORE\)!==encoded\)/);
console.log('Backup export browser/native bridge path, failure messaging, retry, restore guard passed');
