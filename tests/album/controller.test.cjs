const assert=require('node:assert/strict');
const {create}=require('../../src/main/resources/assets/muxi_terminal/html/terminal/album-controller.js');
async function main(){
  const commands=[],a={id:'a'},b={id:'b'};let live=[a,b],trash=[],cancelled=0;
  const invoke=async command=>{
    commands.push(command);
    if(command==='album.photos')return live.slice();if(command==='album.recycled')return trash.slice();
    if(command.startsWith('album.photo:') || command.startsWith('album.thumb:') || command.startsWith('album.recycled-photo:'))return 'data:image/png;base64,TEST';
    if(command.startsWith('album.prepare-delete:'))return {id:command.slice(21),token:'one-use-ticket'};
    if(command==='album.cancel-delete')cancelled++;
    if(command==='album.recycle:one-use-ticket'){const p=live.shift();trash.push(p);}
    if(command.startsWith('album.restore:')){live.push(trash.shift());}
    return {ok:true};
  };
  const c=create({invoke});await c.load();assert.equal(c.getState().photos.length,2);
  live.push({id:"F2-new.png",modified:100,bytes:20});await c.refresh();assert.equal(c.getState().photos.length,3);live.pop();await c.refresh();
  await c.open('a');assert(c.getState().image);await c.prepareDelete();
  assert.equal(c.getState().confirm.id,'a');assert(!commands.some(x=>x.startsWith('album.recycle:')));
  await c.cancelDelete();assert.equal(c.getState().confirm,null);assert.equal(live.length,2);
  await assert.rejects(c.confirmDelete(),/先确认/);
  await c.prepareDelete();await c.confirmDelete();assert.equal(live.length,1);assert.equal(trash.length,1);assert.equal(c.getState().image,'');
  await c.load(true);await c.open('a');await c.restore();assert.equal(trash.length,0);assert.equal(live.length,2);
  await c.load();await c.open('b');await c.closePhoto();assert.equal(c.getState().image,'');assert.equal(c.getState().selected,null);
  await assert.rejects(c.open('missing'),/不存在/);
  let resolveRead,changes=[];
  const d=create({invoke:cmd=>cmd==='album.photos'?new Promise(r=>resolveRead=r):Promise.resolve({ok:true}),onChange:s=>changes.push(s)});
  const load=d.load();await d.dispose();const after=changes.length;resolveRead([a]);await load;assert.equal(changes.length,after);assert.equal(d.getState().photos.length,0);
  let resolveThumb;const e=create({invoke:cmd=>cmd==='album.photos'?Promise.resolve([a]):cmd.startsWith('album.thumb:')?new Promise(r=>resolveThumb=r):Promise.resolve({ok:true})});
  await e.load();const thumb=e.thumb('a');await e.dispose();resolveThumb('late');assert.equal(await thumb,null);
  let resolveConfirm;const f=create({invoke:cmd=>cmd==='album.photos'?Promise.resolve([a]):cmd.startsWith('album.photo:')?Promise.resolve('image'):cmd.startsWith('album.prepare-delete:')?new Promise(r=>resolveConfirm=r):Promise.resolve({ok:true})});
  await f.load();await f.open('a');const confirmation=f.prepareDelete();await f.dispose();resolveConfirm({id:'a',token:'late'});await confirmation;await assert.rejects(f.confirmDelete(),/先确认/);
  const g=create({invoke:async cmd=>{if(cmd==='album.photos')return [a];if(cmd.startsWith('album.photo:'))return 'image';if(cmd.startsWith('album.prepare-delete:'))return {id:'a',token:'t'};if(cmd.startsWith('album.recycle:'))throw Error('expired');return {ok:true};}});
  await g.load();await g.open('a');await g.prepareDelete();await assert.rejects(g.confirmDelete(),/expired/);assert.equal(g.getState().confirm,null);assert.equal(g.getState().photos.length,1);
  assert(commands.every(x=>x.startsWith('album.')));assert(cancelled>0);
  console.log(JSON.stringify({success:true,checks:16,coverage:['shared list','read-only thumbnail','cancel before mutation','one-use confirmed delete','restore','released viewer pixels','unknown id','stale list/thumbnail/confirmation','expired mutation re-confirm','album-only requests']}));
}
main().catch(error=>{console.error(error);process.exitCode=1;});
