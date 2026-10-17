/** Minimal no-network DOM shim: prove all startup scripts finish without a fatal error.
    Browser camera / Android compilation are tested separately. */
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const scripts=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)].map(x=>x[1]);
function element(){return {style:{},dataset:{},value:'',innerHTML:'',textContent:'',classList:{toggle(){},add(){},remove(){}},addEventListener(){},querySelector(){return element()},querySelectorAll(){return []},replaceChildren(){},appendChild(){},scrollIntoView(){},setAttribute(){},getContext(){return {drawImage(){},getImageData(){return {data:new Uint8ClampedArray(400)}}}}}}
const storage=new Map();
const document={querySelector(){return element()},getElementById(){return element()},querySelectorAll(){return []},addEventListener(){},createElement(){return element()},body:element(),head:element()};
const localStorage={getItem(k){return storage.has(k)?storage.get(k):null},setItem(k,v){storage.set(k,String(v))},removeItem(k){storage.delete(k)}};
const context={document,localStorage,navigator:{onLine:false,userAgent:'ManaVaultRuntimeTest'},window:{addEventListener(){}},console,Date,Math,JSON,Number,String,Array,Object,Set,Map,Promise,Error,RegExp,URL,URLSearchParams,setTimeout(){return 1},clearTimeout(){},fetch(){return Promise.reject(new Error('Offline test'))},alert(){},confirm(){return false},Tesseract:{},ManaVaultCardDB:{init(){},get(){return Promise.resolve(null)}},Intl,performance:{now(){return 0}},Image:function(){}};
context.window.window=context.window;context.window.navigator=context.navigator;context.window.localStorage=localStorage;context.window.document=document;
vm.createContext(context);
for(const script of scripts){vm.runInContext(script,context,{filename:'index.html'});}
assert.equal(typeof context.window.mvNativeScanBatch,'function','Native batch callback not initialized');
assert.equal(typeof context.window.mvNativeScanResult,'function','Native result callback not initialized');
assert.equal(typeof context.window.mvNativeLaunchNow,'function','Native launch button handler not initialized');
console.log('Full JavaScript startup executed without fatal error; native batch and review handlers exist.');
