/* v5.12.15: destructive collection actions must be deliberate and reversible. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const base=path.resolve(__dirname,'..');
const js=fs.readFileSync(path.join(base,'app/src/main/assets/collection-safety.js'),'utf8');
const html=fs.readFileSync(path.join(base,'app/src/main/assets/index.html'),'utf8');
const fakeStore=new Map();
const localStorage={getItem:key=>fakeStore.get(key)||null,setItem:(key,value)=>fakeStore.set(key,String(value)),removeItem:key=>fakeStore.delete(key)};
function fixture(cards){
 const buttons={'[data-inc]':[], '[data-dec]':[], '[data-trade]':[]};
 const document={querySelectorAll:query=>buttons[query]||[],getElementById:()=>null,body:null};
 const ctx={window:{},localStorage,document,state:{cards, decks:[],trades:[]},esc:s=>String(s),render(){ctx.rendered++},save(){ctx.saved++;localStorage.setItem('manavault-v1',JSON.stringify(ctx.state));ctx.render()},saved:0,rendered:0,Date,JSON,Math,Number,Set,Promise,String,console};
 vm.createContext(ctx);vm.runInContext(js,ctx);
 return {ctx,safety:ctx.window.mvCollectionSafety,buttons};
}
const card=(id,qty)=>({id,name:id==='a'?'Soul-Guide Lantern':'Arbor Elf',set:'thb',set_name:'Theros Beyond Death',collector_number:'237',condition:'LP',foil:true,qty,price:1.25,oracle_text:'Test'});
(async()=>{
 fakeStore.clear();
 const {ctx,safety,buttons}=fixture([card('a',1),card('b',2)]);
 assert.equal(await safety.decrement('a',()=>false),'cancelled','Final copy requires affirmative confirmation');
 assert.equal(ctx.state.cards.length,2);
 assert.equal(ctx.saved,0);
 assert.equal(await safety.decrement('a',()=>true),'removed');
 assert.equal(ctx.state.cards.length,1,'Last copy removed only after explicit confirmation');
 assert.match(safety.undoPanel(),/Soul-Guide Lantern/);
 assert.ok(fakeStore.get('mv_collection_quantity_undo_v1'),'Removal undo journal is durable');
 assert.equal(safety.undo(),true);
 assert.deepEqual(JSON.parse(JSON.stringify(ctx.state.cards[0])),card('a',1),'Undo restores printing, metadata and quantity');
 assert.equal(ctx.state.cards.length,2);
 assert.equal(await safety.decrement('b',()=>{throw Error('Should not confirm 2 → 1')}),'removed');
 assert.equal(ctx.state.cards[1].qty,1);
 assert.equal(safety.undo(),true);
 assert.equal(ctx.state.cards[1].qty,2);
 assert.equal(await safety.decrement('missing',()=>true),'missing');
 // Persist an undo across an app restart. All attributes must survive.
 await safety.decrement('a',()=>true);
 const restarting=fixture(JSON.parse(fakeStore.get('manavault-v1')).cards);
 assert.ok(restarting.safety.last());
 assert.equal(restarting.safety.undo(),true);
 assert.equal(restarting.ctx.state.cards[0].name,'Soul-Guide Lantern');
 // Confirmation races must not remove a different collection quantity.
 let finish;
 const delayed=new Promise(resolve=>finish=resolve);
 const started=restarting.safety.decrement('a',()=>delayed);
 restarting.ctx.state.cards[0].qty=3;
 finish(true);
 assert.equal(await started,'changed');
 assert.equal(restarting.ctx.state.cards[0].qty,3);
 // Binding is shared by normal and filtered collection views.
 const decButton={dataset:{dec:'a'}};
 buttons['[data-dec]'].push(decButton);
 safety.bind();
 assert.equal(typeof decButton.onclick,'function');
 assert.match(html,/function bindCollection\(\)\{mvCollectionSafety\.bind\(\)\}/);
 assert.match(html,/mvCollectionSafety\.undoPanel\(\)/);
 assert.match(html,/mvCollectionSafety\.clearHistory\(\)/);
 assert.match(html,/collection-safety\.js/);
 assert.doesNotMatch(html,/if\(--c\.qty<=0\)/,'Must not retain old unconditional final-copy deletion');
 restarting.safety.clearHistory();
 assert.equal(restarting.safety.last(),null,'A data reset invalidates undo');
 console.log('Collection decrement confirmation, filtered binding, persistent undo, metadata restoration, stale-state and clear tests passed.');
})().catch(e=>{console.error(e);process.exitCode=1});
