/** Tests the actual JS decision helper with simulated native symbol and artwork evidence.
 * These are deterministic confidence-gate tests, NOT real-phone image accuracy tests.
 */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const from=html.indexOf('function mv61CollectorKey('),end=html.indexOf('function mv61NormalImage(');
const later=html.indexOf('// v5.12.23: Merge three INDEPENDENT');
const endLater=html.indexOf('async function mv61ResolvePrinting(',later);
assert.ok(from>0&&end>from&&later>0&&endLater>later);
const ctx={window:{},setTimeout,clearTimeout,Promise};
vm.createContext(ctx);
vm.runInContext(html.slice(from,end),ctx);
vm.runInContext(html.slice(later,endLater),ctx);
const row=(text,zone='footer-left')=>({text,zone,pass:'color'});
const scry=(id,set,number,name)=>({id,set,collector_number:number,name,rarity:'common'});
const cases={
 lantern:[scry('brr-54','brr','54','Soul-Guide Lantern'),scry('thb-237','thb','237','Soul-Guide Lantern'),scry('brc-139','brc','139','Soul-Guide Lantern')],
 cameo:[scry('inv-314','inv','314','Tigereye Cameo'),scry('c13-258','c13','258','Tigereye Cameo')],
 lone:[scry('7ed-254','7ed','254','Lone Wolf'),scry('uds-111','uds','111','Lone Wolf')],
 light:[scry('tmp','tmp','13','Light of Day'),scry('6ed','6ed','27','Light of Day')],
 shred:[scry('rtr','rtr','221','Rakdos Shred-Freak'),scry('uma','uma','225','Rakdos Shred-Freak')],
 harvester:[scry('vow','vow','104','Desperate Farmer // Depraved Harvester'),scry('alt','dbl','104','Desperate Farmer // Depraved Harvester')]
};
const perform=async(lines,candidates,{set,score=.95,margin=.19,art=null,exact=true}={})=>{
 ctx.lines=lines;ctx.candidates=candidates;
 ctx.setEvidence={set,score,margin};ctx.artEvidence=art;
 vm.runInContext('mv61RankSetSymbols=async()=>setEvidence;mv115RankBundledArtwork=async()=>artEvidence||{reason:"no complete art library"}',ctx);
 return await vm.runInContext('mv123ResolvePhysicalFusion(3,{lines},candidates,{exactTitle:'+exact+'})',ctx);
};
(async()=>{
 let out=await perform([row('54')],cases.lantern,{set:'brr',score:.91,margin:.17});
 assert.equal(out.printing?.id,'brr-54','short collector 54 plus BRR logo must identify the retro printing');
 out=await perform([row('314/350')],cases.cameo,{set:'inv',score:.90,margin:.14});
 assert.equal(out.printing?.id,'inv-314','old Invasion numbered card must resolve via symbol and numerator');
 out=await perform([row('254/350')],cases.lone,{set:'7ed',score:.91,margin:.12});
 assert.equal(out.printing?.id,'7ed-254','Seventh Edition must resolve from visual symbol and numerator');
 out=await perform([row('221/274')],cases.shred,{set:'rtr',score:.89,margin:.14});
 assert.equal(out.printing?.id,'rtr','Return to Ravnica must resolve');
 out=await perform([row('104/277')],cases.harvester,{set:'vow',score:.90,margin:.15});
 assert.equal(out.printing?.id,'vow','transform card reverse face collector must resolve');
 out=await perform([],cases.light,{set:'tmp',score:.98,margin:.2});
 assert.equal(out.printing?.id,'tmp','old frame without reliable collector can resolve only on strong exact-title + set symbol');
 out=await perform([],cases.light,{set:'tmp',score:.90,margin:.12});
 assert.equal(out.printing,undefined,'moderate standalone symbol must not force a printing');
 out=await perform([],cases.light,{set:'tmp',score:.90,margin:.12,art:{winningIds:['tmp'],score:.976,margin:.14}});
 assert.equal(out.printing?.id,'tmp','art plus moderately strong logo and exact title can verify');
 out=await perform([row('54')],cases.lantern,{set:'thb',score:.96,margin:.2});
 assert.equal(out.printing,undefined,'conflicting collector and symbol never auto-save');
 out=await perform([row('54'),row('237/280','footer-deep')],cases.lantern,{set:'brr',score:.99,margin:.3});
 assert.equal(out.printing,undefined,'two different fractions must not be suppressed');
 out=await perform([row('54')],[cases.lantern[0],{...cases.lantern[0],id:'brr-collision'}],{set:'brr',score:.98,margin:.19});
 assert.equal(out.printing,undefined,'set/# printings colliding by edition cannot be guessed');
 out=await perform([row('54')],cases.lantern,{set:'brr',score:.99,margin:.3,exact:false});
 assert.equal(out.printing,undefined,'unconfirmed identity cannot be promoted by symbol alone');
 assert.match(html,/p\.layout==='split'\|\|p\.layout==='aftermath'/,'true split vs double-faced gate is required');
 const java=fs.readFileSync(__dirname+'/../app/src/main/java/com/manavault/app/PrintingFingerprintVerifier.java','utf8');
 assert.match(java,/\.695f/,'lower old-frame type lines must be inspected');
 console.log('v5.12.23 physical-printing fusion: 12 identity, ambiguity, old-frame and DFC checks passed');
})().catch(e=>{console.error(e);process.exitCode=1});
