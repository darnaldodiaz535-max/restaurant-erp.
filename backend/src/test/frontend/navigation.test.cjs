const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const staticRoot=path.join(__dirname,'../../main/resources/static');
function node(){const classes=new Set();return {hidden:true,disabled:false,textContent:'',handlers:{},style:{setProperty(){},removeProperty(){}},classList:{add(x){classes.add(x)},remove(x){classes.delete(x)},toggle(x,on){on?classes.add(x):classes.delete(x)}},addEventListener(k,cb){this.handlers[k]=cb},setAttribute(){},removeAttribute(){}}}
function fixture(){
 const elements=Object.fromEntries(['refreshHomeButton','dashboardHomeButton','pullRefreshIndicator','screenRefreshStatus'].map(id=>[id,node()]));
 const handlers={};let loads=0,scroll=null;
 const context={Map,Set,Promise,setTimeout:()=>0,clearTimeout(){},console,currentProfile:{id:1},screenRevision:1,
 dashboard:{hidden:true},personal:{hidden:false},otherModule:{querySelectorAll:()=>[]},deletingEmployees:new Set(),
 document:{body:{},getElementById:id=>elements[id],querySelector:()=>null,addEventListener:(event,cb)=>{handlers[event]=cb}},
 window:{scrollX:0,scrollY:0,scrollTo(p){scroll=p;this.scrollY=p.top}},matchMedia:()=>({matches:false}),getComputedStyle:()=>({overflowX:'visible'}),
 menuItems:[{hidden:false,label:'Dashboard'}],navLabel:link=>link.label,showScreen:link=>{context.destination=link.label},
 loadEmployees:async()=>{loads++},loadDashboardMetrics:async()=>{loads++}};
 vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(staticRoot,'app-navigation.js'),'utf8'),context);
 return {context,elements,handlers,loads:()=>loads,scroll:()=>scroll,run:code=>vm.runInContext(code,context)};
}
function touch(x,y,target={closest:()=>null,parentElement:null}){return {target,touches:[{clientX:x,clientY:y}],cancelable:true,preventDefault(){this.prevented=true}}}
test('refresh coalesces overlapping requests and keeps the view and scroll',async()=>{
 const f=fixture();let release;let count=0;f.context.window.scrollY=240;
 f.context.loadEmployees=()=>{count++;return new Promise(resolve=>{release=resolve})};
 const a=f.run('refreshCurrentScreen()'),b=f.run('refreshCurrentScreen()');assert.equal(a,b);assert.equal(count,1);
 assert.equal(f.elements.pullRefreshIndicator.hidden,false);release();await a;
 assert.equal(f.scroll().top,240);assert.equal(f.elements.pullRefreshIndicator.hidden,true);assert.equal(f.elements.refreshHomeButton.disabled,false);
});
test('refresh failure shows a clear error and leaves existing controls alone',async()=>{
 const f=fixture();f.context.personal.filter='Salón';f.context.loadEmployees=async()=>{throw Error('Sin conexión')};
 assert.equal(await f.run('refreshCurrentScreen()'),false);assert.match(f.elements.screenRefreshStatus.textContent,/Sin conexión/);
 assert.equal(f.context.personal.filter,'Salón');assert.equal(f.elements.pullRefreshIndicator.hidden,true);
});
test('pull gesture refreshes only after a deliberate vertical pull at top',async()=>{
 const f=fixture();f.handlers.touchstart(touch(10,10));const move=touch(10,150);f.handlers.touchmove(move);
 assert.equal(move.prevented,true);f.handlers.touchend();await new Promise(setImmediate);assert.equal(f.loads(),1);
 f.context.window.scrollY=100;f.handlers.touchstart(touch(10,10));f.handlers.touchmove(touch(10,180));f.handlers.touchend();await new Promise(setImmediate);assert.equal(f.loads(),1);
 f.context.window.scrollY=0;f.handlers.touchstart(touch(10,10));f.handlers.touchmove(touch(130,40));f.handlers.touchend();await new Promise(setImmediate);assert.equal(f.loads(),1);
 f.handlers.touchstart(touch(10,10,{closest:()=>true}));f.handlers.touchmove(touch(10,180));f.handlers.touchend();await new Promise(setImmediate);assert.equal(f.loads(),1);
});
test('refresh skips stale module callbacks and can return the dashboard to top',async()=>{
 const f=fixture();f.context.personal.hidden=true;f.context.called=0;
 f.run('registerScreenRefresh(async()=>{called++}); screenRevision++');await f.run('refreshCurrentScreen()');assert.equal(f.context.called,0);
 f.context.dashboard.hidden=false;f.context.window.scrollY=800;await f.run('refreshCurrentScreen({toTop:true})');assert.equal(f.scroll().top,0);
 f.elements.dashboardHomeButton.handlers.click();assert.equal(f.context.destination,'Dashboard');
});
test('staff deletion submits once, removes row immediately and is independent of follow-up reloads',async()=>{
 const source=fs.readFileSync(path.join(staticRoot,'scrip.js'),'utf8');
 const start=source.indexOf('employeeBody?.addEventListener("click"');const end=source.indexOf('\nlet currentProfile',start);
 let listener,release,deletes=0,drawn,status;
 const button={disabled:false,textContent:'Eliminar',closest:()=>row};
 const row={dataset:{id:'2'},cells:[{textContent:'Prueba'},{textContent:'Mesero'},{textContent:'Salón'},{querySelector:()=>({classList:{contains:()=>true}})}],querySelectorAll:()=>[button]};
 const context={console,employeeBody:{addEventListener:(name,cb)=>{listener=cb}},deletingEmployees:new Set(),confirm:()=>true,isOwnerRole:()=>true,
 employeesApi:'/api/empleados',employeesRequest:0,employeesSnapshot:[{id:1},{id:2}],apiRequest:()=>{deletes++;return new Promise(resolve=>{release=resolve})},
 drawEmployees:people=>{drawn=people},showRefreshStatus:message=>{status=message},localStorage:{removeItem(){}},loadDashboardMetrics:async()=>{},alert(){throw Error('Unexpected alert')}};
 vm.createContext(context);vm.runInContext(source.slice(start,end),context);
 const event={target:{closest:()=>button}};const first=listener(event);await listener(event);assert.equal(deletes,1);assert.equal(button.disabled,true);
 release();await first;assert.equal(drawn.length,1);assert.equal(drawn[0].id,1);assert.match(status,/eliminación completada/);assert.equal(context.employeesRequest,1);
});
