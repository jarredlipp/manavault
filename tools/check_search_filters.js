/** Regression checks for the v5.11.3 offline collection and catalog filters. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const begin=html.lastIndexOf('//',html.indexOf('Combined, offline collection-search filters.'));
const end=html.indexOf('</script>',begin);
assert.ok(begin>0&&end>begin);
const ctx={
 window:{filterQuery:''},mvCollectionSafety:{undoPanel:()=>'',bind:()=>{}},
 state:{cards:[
  {name:'River Hoopoe',set:'hou',set_name:'Hour of Devastation',collector_number:'143',type_line:'Creature — Bird',colors:['G','U'],color_identity:['G','U'],qty:2,price:.1},
  {name:'Sliver Hiveguard',set:'tst',set_name:'Test Set',collector_number:'11',type_line:'Creature — Sliver',colors:['W'],color_identity:['W'],qty:1,price:1},
  {name:'Sol Ring',set:'cmm',set_name:'Commander Masters',collector_number:'396',type_line:'Artifact',colors:[],color_identity:[],qty:1,price:2},
  {name:'Duress',set:'m19',set_name:'Core Set 2019',collector_number:'94',type_line:'Sorcery',colors:['B'],color_identity:['B'],qty:3,price:.05},
  {name:'TBD',set:'',set_name:'Printing unverified',collector_number:'',type_line:'',unverifiedPrinting:true,qty:1,price:null}
 ]},
 collection:()=>'',scanner:()=>'<div id="searchResults"></div>',bind:()=>{},
 search:async()=>{}, tab:'Dashboard',cardRows:arr=>arr.map(c=>c.name).join(', '),
 esc:x=>String(x),document:{getElementById:()=>null},render:()=>{},bindCollection:()=>{},
 scry:async()=>({data:[]}),resultRows:()=>'',wireAdds:()=>{},searchResults:[],
 console
};
vm.createContext(ctx);vm.runInContext(html.slice(begin,end),ctx);
const run=code=>vm.runInContext(code,ctx);
assert.equal(run("mvCollectionFiltered().length"),5);
run("mvCollectionColor='G';mvCollectionType='Creature'");
assert.equal(run("mvCollectionFiltered().map(x=>x.name).join(',')"),'River Hoopoe');
run("mvCollectionColor='C';mvCollectionType='' ");
assert.equal(run("mvCollectionFiltered().map(x=>x.name).join(',')"),'Sol Ring','Unknown unverified metadata must not be classified colorless');
run("mvCollectionColor='';window.filterQuery='m19'");
assert.equal(run("mvCollectionFiltered()[0].name"),'Duress');
run("mvCatalogColor='U';mvCatalogType='Creature';mvCatalogSet='HOU';mvCatalogRarity='uncommon'");
assert.equal(run("mvCatalogQuery('River Hoopoe')"),'name:"River Hoopoe" id>=u t:creature e:hou r:uncommon');
run("mvCatalogColor='C';mvCatalogType='';mvCatalogSet='';mvCatalogRarity=''");
assert.equal(run("mvCatalogQuery('')"),'id:c');
run("mvCollectionColor='';mvCollectionType='Creature';mvCollectionCreature='Sliver';window.filterQuery=''");
assert.equal(run("mvCollectionFiltered().map(x=>x.name).join(',')"),'Sliver Hiveguard');
assert.equal(run("mvCreatureSubtypeMatch('Creature — Elf Warrior','Elf')"),true);
assert.equal(run("mvCreatureSubtypeMatch('Creature — Elf Warrior','Wolf')"),false);
assert.equal(run("mvCreatureSubtypeMatch('Legendary Creature — Time Lord Doctor','Time Lord')"),true);
assert.equal(run("mvCreatureSubtypeMatch('Artifact — Equipment','Equipment')"),false);
run("mvCatalogCreature='Sliver';mvCatalogColor='';");
assert.equal(run("mvCatalogQuery('')"),'t:sliver');
assert.match(run('scanner()'),/id="mvCatalogCreature"/);
assert.match(run('collection()'),/id="mvFilterCreature"/);
assert.match(run('scanner()'),/id="mvCatalogColor"/);
assert.match(run('collection()'),/id="mvFilterType"/);
console.log('Collection name/color/type/set filters and advanced catalog query tests passed.');
