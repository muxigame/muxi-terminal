from pathlib import Path
root=Path(__file__).resolve().parents[2]
assets=root/'src/main/resources/assets/muxi_terminal/html/terminal'
html=(assets/'index.html').read_text(encoding='utf-8')
stub='''<script>
window.qaCommands=[];
window.qaState={active:false,busy:false,mode:'forward',sequence:1,preview:'',saved:'',error:''};
const canvas=document.createElement('canvas');canvas.width=640;canvas.height=360;
const context=canvas.getContext('2d');context.fillStyle='#557b50';context.fillRect(0,0,640,360);context.fillStyle='#f4ebd1';context.font='24px sans-serif';context.fillText('SYNTHETIC UI TEST / NOT GAME RENDER',35,180);
window.qaPreview=canvas.toDataURL('image/png');
window.muxiTerminalQuery=({request,onSuccess,onFailure})=>setTimeout(()=>{
  qaCommands.push(request);let answer={ok:true};
  if(request==='camera.begin'){qaState.active=true;qaState.preview=qaPreview;}
  if(request==='camera.stop'){qaState.active=false;qaState.preview='';}
  if(request.startsWith('camera.mode:'))qaState.mode=request.slice(12);
  if(request==='camera.shutter')qaState.saved='muxi-20261001-000000-000-00000000-0000-0000-0000-000000000000.png';
  if(request==='camera.state')answer={...qaState};
  if(request==='camera.photos')answer=qaState.saved?[{id:qaState.saved,bytes:256,modified:1}]:[];
  if(request.startsWith('camera.photo:'))answer=qaPreview;
  onSuccess(JSON.stringify(answer));
},10);
</script>'''
html=html.replace('<head>','<head>\n<base href="'+assets.as_uri()+'/">\n'+stub,1)
(Path(__file__).parent/'harness.html').write_text(html,encoding='utf-8')
# Reuse the project's dependency-free headless Chrome/CDP runner infrastructure.
source=(root/'tests/app-transition/run-browser-qa.mjs').read_text(encoding='utf-8')
prefix=source[:source.index("  await cdp('Page.enable')")]
prefix=prefix.replace("pathToFileURL(path.join(directory, 'demo.html')).href", "pathToFileURL(path.join(directory, 'harness.html')).href+'#/camera'")
tests=r'''
  await cdp('Page.enable'); await cdp('Runtime.enable');
  await cdp('Emulation.setDeviceMetricsOverride',{width:960,height:660,deviceScaleFactor:1,mobile:false});
  await pause(1500);const tests=[];
  async function check(name,expression){tests.push({name,passed:!!await evaluate(expression)});}
  await check('camera route inside fixed existing shell','document.querySelector("#camera.page-active") && document.querySelectorAll(".shell").length===1 && document.querySelectorAll("#cameraApp").length===1');
  await check('private native first preview enables shutter','!document.querySelector("#cameraShutter").disabled && document.querySelector("#cameraPreview").src.startsWith("data:image/png;")');
  await screenshot('01-forward-synthetic');
  await evaluate('document.querySelector("#cameraSelfie").click()');await pause(700);
  await check('selfie selected','document.querySelector("#cameraSelfie").getAttribute("aria-pressed")==="true" && qaCommands.includes("camera.mode:selfie")');
  await evaluate('document.querySelector("#cameraForward").click()');await pause(700);
  await check('forward restored','document.querySelector("#cameraForward").getAttribute("aria-pressed")==="true"');
  await evaluate('document.querySelector("#cameraShutter").click()');await pause(700);
  await check('saved filename shown safely as text','document.querySelector("#cameraStatus").textContent.includes("muxi-20261001")');
  await evaluate('document.querySelector("#cameraAlbum").click()');await pause(500);
  await check('album stops capture and drops preview pixels','!qaState.active && !document.querySelector("#cameraPreview").hasAttribute("src") && document.querySelectorAll("#cameraPhotoList button").length===1');
  await evaluate('document.querySelector("#cameraPhotoList button").click()');await pause(200);
  await check('local photo viewer','!document.querySelector("#cameraPhoto").hidden && document.querySelector("#cameraPhoto").src.startsWith("data:image/png;")');
  await screenshot('02-album-synthetic');
  await evaluate('document.querySelector("#cameraReturn").click()');await pause(700);
  await check('return releases viewed image and resumes camera','qaState.active && !document.querySelector("#cameraPhoto").hasAttribute("src")');
  await evaluate('window.dispatchEvent(new KeyboardEvent("keydown",{key:"ArrowRight",bubbles:true}))');
  await check('inherits shell keyboard selection','document.querySelector("#camera button.keyboard-selected")===document.activeElement');
  const beforeSpace=await evaluate('qaCommands.filter(x=>x==="camera.shutter").length');
  await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:' ',code:'Space',windowsVirtualKeyCode:32});
  await cdp('Input.dispatchKeyEvent',{type:'keyUp',key:' ',code:'Space',windowsVirtualKeyCode:32});await pause(250);
  tests.push({name:'space shutter from focused control without duplicate click',passed:await evaluate('qaCommands.filter(x=>x==="camera.shutter").length')===beforeSpace+1});
  await cdp('Emulation.setDeviceMetricsOverride',{width:420,height:500,deviceScaleFactor:1,mobile:false});await pause(200);
  await check('small viewport has no horizontal overflow','document.querySelector("#camera").scrollWidth<=document.querySelector("#camera").clientWidth');
  await screenshot('03-small-synthetic');
  await evaluate('location.hash="/home"');await pause(500);
  await check('route exit stops preview and clears pixels','!qaState.active && !document.querySelector("#cameraPreview").hasAttribute("src")');
  tests.push({name:'no browser exceptions',passed:errors.length===0,errors});
  const report={synthetic:true,passed:tests.filter(t=>t.passed).length,total:tests.length,tests};
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
  process.exitCode=report.passed===report.total?0:1;
  await cdp('Browser.close');
} finally {if(socket && socket.readyState===WebSocket.OPEN)socket.close();if(child.exitCode===null)child.kill();}
'''
(Path(__file__).parent/'run-browser-qa.mjs').write_text(prefix+tests,encoding='utf-8')
print('Prepared synthetic camera UI QA; never starts Minecraft')
