/** The symbol ranking API must reject matches that are ambiguous at the SAME crop. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const begin=html.indexOf('async function mv61RankSetSymbols(');
const end=html.indexOf('function mv131ArtworkLimit(',begin);
assert(begin>0&&end>begin);
const ctx={Promise};vm.createContext(ctx);
vm.runInContext(html.slice(begin,end),ctx);
const cards=[{set:'inv'},{set:'rtr'}];
(async()=>{
 ctx.mv61Request=async()=>({matches:[{set:'inv',score:.975},{set:'rtr',score:.63}],failed:0,
   coLocatedAmbiguous:true,coLocatedMargin:.042});
 ctx.cards=cards;
 let result=await vm.runInContext('mv61RankSetSymbols(1,cards,{provisional:true})',ctx);
 assert.equal(result.set,undefined,'Untrustworthy image crop must not establish set');
 assert.match(result.reason,/same photographed position/);
 ctx.mv61Request=async()=>({matches:[{set:'inv',score:.975},{set:'rtr',score:.63}],failed:0,
  coLocatedAmbiguous:false,coLocatedMargin:.22});
 result=await vm.runInContext('mv61RankSetSymbols(1,cards,{provisional:true})',ctx);
 assert.equal(result.set,'inv','Distinctive co-located symbol should pass to independent collector/title guard');
 ctx.mv61Request=async()=>({matches:[{set:'inv',score:.975},{set:'rtr',score:.965}],failed:0,
  coLocatedAmbiguous:false,coLocatedMargin:.22});
 result=await vm.runInContext('mv61RankSetSymbols(1,cards,{provisional:true})',ctx);
 assert.equal(result.set,undefined,'Near-tied global symbols must still require review');
 console.log('v5.12.32 set symbol co-location and competitor veto PASSED');
})().catch(e=>{console.error(e);process.exitCode=1});
