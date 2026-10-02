from pathlib import Path
root=Path(__file__).resolve().parents[2];assets=root/'src/main/resources/assets/muxi_terminal/html/terminal'
html=(assets/'index.html').read_text(encoding='utf-8')
stub='''<script>
window.qaCommands=[];window.qaPending=null;window.qaLive=[],window.qaTrash=[];
const id=n=>'muxi-20261001-00000'+n+'-000-00000000-0000-0000-0000-00000000000'+n+'.png';
qaLive.push({id:id(1),bytes:256,modified:1},{id:id(2),bytes:256,modified:2});
const canvas=document.createElement('canvas');canvas.width=640;canvas.height=360;
const context=canvas.getContext('2d');context.fillStyle='#557b50';context.fillRect(0,0,640,360);context.fillStyle='#f4ebd1';context.font='24px sans-serif';context.fillText('SYNTHETIC ALBUM UI / NOT GAME RENDER',20,180);
window.qaPreview=canvas.toDataURL('image/png');window.qaState={active:false,busy:false,mode:'forward',sequence:1,preview:'',saved:'',error:''};
window.muxiTerminalQuery=({request,onSuccess,onFailure})=>setTimeout(()=>{
  qaCommands.push(request);let answer={ok:true};
  if(request==='album.photos' || request==='camera.photos')answer=qaLive.slice();
  if(request==='album.recycled')answer=qaTrash.slice();
  if(/^(album\\.(thumb|photo|recycled-thumb|recycled-photo)|camera\\.photo):/.test(request))answer=qaPreview;
  if(request.startsWith('album.prepare-delete:'))answer=qaPending={id:request.slice(21),token:'00000000-0000-0000-0000-000000000000'};
  if(request==='album.cancel-delete')qaPending=null;
  if(request.startsWith('album.recycle:')){if(!qaPending){onFailure(400,'confirmation required');return;}const photo=qaLive.find(p=>p.id===qaPending.id);qaLive=qaLive.filter(p=>p!==photo);qaTrash.push(photo);qaPending=null;}
  if(request.startsWith('album.restore:')){const photo=qaTrash.find(p=>p.id===request.slice(14));qaTrash=qaTrash.filter(p=>p!==photo);qaLive.push(photo);}
  if(request==='camera.begin'){qaState.active=true;qaState.preview=qaPreview;}
  if(request==='camera.stop'){qaState.active=false;qaState.preview='';}
  if(request.startsWith('camera.mode:'))qaState.mode=request.slice(12);
  if(request==='camera.state')answer={...qaState};
  if(request==='camera.shutter'){qaState.saved=id(3);qaLive.push({id:qaState.saved,bytes:256,modified:3});}
  onSuccess(JSON.stringify(answer));
},10);
</script>'''
html=html.replace('<head>','<head>\n<base href="'+assets.as_uri()+'/">\n'+stub,1)
(Path(__file__).parent/'harness.html').write_text(html,encoding='utf-8')
source=(root/'tests/app-transition/run-browser-qa.mjs').read_text(encoding='utf-8')
prefix=source[:source.index("  await cdp('Page.enable')")].replace("pathToFileURL(path.join(directory, 'demo.html')).href","pathToFileURL(path.join(directory, 'harness.html')).href+'#/album'")
tests=r'''
  await cdp('Page.enable');await cdp('Runtime.enable');
  await cdp('Emulation.setDeviceMetricsOverride',{width:960,height:660,deviceScaleFactor:1,mobile:false});await pause(1000);
  const tests=[];async function check(name,expression){tests.push({name,passed:!!await evaluate(expression)});}
  async function click(selector){await evaluate('document.querySelector('+JSON.stringify(selector)+').click()');await pause(220);}
  async function key(key,code=key){await cdp('Input.dispatchKeyEvent',{type:'keyDown',key,code});await cdp('Input.dispatchKeyEvent',{type:'keyUp',key,code});await pause(150);}
  await check('independent home card and fixed shell','document.querySelectorAll("#albumApp").length===1 && document.querySelectorAll(".shell").length===1 && document.querySelector("#album.page-active")');
  await check('shared installed-app count observes album card','document.querySelector("#installedAppCount").textContent.startsWith(String(document.querySelectorAll("#home .app-card").length))');
  await check('original modules retained','document.querySelector("#camera") && document.querySelector("#settings") && document.querySelector("#music") && document.querySelector("#games")');
  await check('native lazy thumbnail grid','document.querySelectorAll("#albumGrid .album-tile").length===2 && document.querySelector("#albumGrid img").src.startsWith("data:image/png;")');
  await screenshot('01-thumbnail-grid-synthetic');
  await click('#albumGrid button');
  await check('viewer opens and thumbnail bytes released','!document.querySelector("#albumViewer").hidden && document.querySelector("#albumImage").src.startsWith("data:image/png;") && !document.querySelector("#albumGrid img").hasAttribute("src")');
  await click('#albumZoomIn');await check('zoom control','document.querySelector("#albumImage").style.width==="150%"');await click('#albumFit');
  await click('#albumDelete');
  await check('clear confirmation before any mutation','!document.querySelector("#albumConfirm").hidden && qaLive.length===2 && !qaCommands.some(x=>x.startsWith("album.recycle:"))');
  await check('safe default focus and background excluded','document.activeElement.id==="albumCancelDelete" && [...document.querySelectorAll("#albumBody button")].every(b=>b.disabled)');
  await screenshot('02-confirmation-synthetic');
  await key('Tab');await check('shared Tab enters only confirm control','document.activeElement.id==="albumConfirmDelete"');
  await key('Escape');await check('Escape cancels without navigating or deleting','location.hash==="#/album" && document.querySelector("#albumConfirm").hidden && qaLive.length===2 && document.activeElement.id==="albumDelete"');
  await click('#albumDelete');await click('#albumCancelDelete');await check('explicit cancel keeps original','qaLive.length===2 && qaTrash.length===0');
  await click('#albumDelete');await click('#albumConfirmDelete');
  await check('confirmed recycle refreshes shared list and clears viewed bytes','qaLive.length===1 && qaTrash.length===1 && document.querySelectorAll("#albumGrid button").length===1 && !document.querySelector("#albumImage").hasAttribute("src")');
  await click('#albumRecycleTab');await click('#albumGrid button');
  await check('recycle viewer supports restore and hides deletion','!document.querySelector("#albumRestore").hidden && document.querySelector("#albumDelete").hidden');
  await screenshot('03-recycle-view-synthetic');await click('#albumRestore');await click('#albumPhotosTab');
  await check('restored photo immediately visible','qaLive.length===2 && qaTrash.length===0 && document.querySelectorAll("#albumGrid button").length===2');
  await evaluate('location.hash="/camera"');await pause(700);await click('#cameraShutter');await pause(500);
  await check('camera save uses shared library','qaLive.length===3 && qaState.saved');
  await check('camera retains embedded album and independent link','document.querySelector("#cameraAlbum") && document.querySelector("#cameraOpenAlbum")');
  await evaluate('location.hash="/album"');await pause(700);
  await check('new camera photo immediately present after album switch','document.querySelectorAll("#albumGrid button").length===3');
  await cdp('Emulation.setDeviceMetricsOverride',{width:420,height:500,deviceScaleFactor:1,mobile:false});await pause(200);
  await check('compact viewport has no horizontal overflow','document.querySelector("#album").scrollWidth<=document.querySelector("#album").clientWidth');await screenshot('04-small-grid-synthetic');
  await click('#albumGrid button');await click('#albumDelete');await screenshot('05-small-confirmation-synthetic');
  await check('compact confirmation visible and scrollable','(() => {const r=document.querySelector("#albumConfirm [role=dialog]").getBoundingClientRect();return r.top>=0 && r.bottom<=innerHeight && r.right<=innerWidth;})()');
  await evaluate('location.hash="/home"');await pause(250);
  await check('route exit drops images and pending confirmation','!qaPending && !document.querySelector("#albumImage").hasAttribute("src") && [...document.querySelectorAll("#albumGrid img")].every(i=>!i.hasAttribute("src"))');
  tests.push({name:'no browser runtime exceptions',passed:errors.length===0,errors});
  const report={synthetic:true,passed:tests.filter(t=>t.passed).length,total:tests.length,tests};
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));process.exitCode=report.passed===report.total?0:1;
  await cdp('Browser.close');
}finally{if(socket && socket.readyState===WebSocket.OPEN)socket.close();if(child.exitCode===null)child.kill();}
'''
(Path(__file__).parent/'run-browser-qa.mjs').write_text(prefix+tests,encoding='utf-8')
print('Prepared integrated album UI QA with synthetic in-memory photos only')
