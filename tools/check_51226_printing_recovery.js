/** Regression cases for observed legacy collector fractions and weak-symbol art fallback. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync(__dirname+'/../app/src/main/assets/index.html','utf8');
const first=html.indexOf('function mv61CollectorKey('),last=html.indexOf('function mv61NormalImage(');
const fusion=html.indexOf('// v5.12.23: Merge three INDEPENDENT'),end=html.indexOf('async function mv61ResolvePrinting(',fusion);
assert(first>=0&&last>first&&fusion>last&&end>fusion);
const ctx={window:{},setTimeout,clearTimeout,Promise};vm.createContext(ctx);
vm.runInContext(html.slice(first,last),ctx);
vm.runInContext(html.slice(fusion,end),ctx);
function rows(text){return [{zone:'footer-line',pass:'color',text}];}
function card(id,set,n){return {id,name:'Test Card',set,collector_number:n,rarity:'common'};}
function decide(lines,candidates,exact=true){ctx.lines=lines;ctx.cards=candidates;ctx.exact=exact;
 return vm.runInContext('mv126ResolveStructuredFooter(lines,cards,{titleVerified:exact})',ctx);}
const cameo=[card('inv314','inv','314'),card('c13314','c13','79')];
assert.equal(decide(rows('Illus. Donato Giancola  (c) 1993-2000 Wizards of the Coast, Inc. 314/350'),cameo).printing?.id,'inv314',
 'preserve the printed terminal collector fraction when merged with the copyright');
assert.equal(decide(rows('(c) 1993-2000 Wizards of the Coast Inc. 254/350'),[card('7ed254','7ed','254'),card('c13245','c13','245')]).printing?.id,'7ed254');
assert.equal(decide(rows('(c) 1993/2000 Wizards of the Coast'),[card('fake','old','1993')]).printing,undefined,
 'copyright year ranges must never count as collector numbers');
assert.equal(decide(rows('(c) 1993-2000 Wizards Inc 314/350'),[card('inv314','inv','314'),card('other314','pcomm','314')]).printing,undefined,
 'a shared number must never choose an exact printing');
const artCandidates=[card('rtr221','rtr','221'),card('other99','other','99')];
ctx.lines=rows('221/274');ctx.cards=artCandidates;
vm.runInContext(`mv61RankSetSymbols=async()=>({reason:'Symbol too weak',topScore:.60,margin:.02});
 mv115RankBundledArtwork=async()=>({winningIds:['rtr221'],score:.976,margin:.13});`,ctx);
(async()=>{
 let result=await vm.runInContext('mv123ResolvePhysicalFusion(3,{lines},cards,{exactTitle:true})',ctx);
 assert.equal(result.printing?.id,'rtr221','collector + independently matched offline art should work if symbol matching is weak');
 vm.runInContext(`mv115RankBundledArtwork=async()=>({reason:'Bundled artwork missing candidate'})`,ctx);
 result=await vm.runInContext('mv123ResolvePhysicalFusion(3,{lines},cards,{exactTitle:true})',ctx);
 assert.equal(result.printing,undefined,'missing artwork coverage cannot trigger auto-save');
 assert.match(result.artworkReason,/Bundled artwork missing/);
 vm.runInContext(`mv115RankBundledArtwork=async()=>({winningIds:['rtr221'],score:.976,margin:.13})`,ctx);
 result=await vm.runInContext('mv123ResolvePhysicalFusion(3,{lines},cards,{exactTitle:false})',ctx);
 assert.equal(result.printing,undefined,'unconfirmed title cannot use collector/artwork to save');
 console.log('v5.12.26 legacy footer, offline artwork fallback and conflict guards passed');
})().catch(e=>{console.error(e);process.exitCode=1});
