from pathlib import Path
import importlib.util,json,threading,time,os,hashlib,zipfile,ast
root=Path(__file__).resolve().parent;qa=root/'camera-native-qa'
spec=importlib.util.spec_from_file_location('shared',qa/'shared_progress_io.py')
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)
for p in [qa/'run-camera-qa.py',qa/'build-qa.py',qa/'shared_progress_io.py']:ast.parse(p.read_text(encoding='utf-8'))
lab=qa/'build/io-verification';lab.mkdir(exist_ok=True);target=lab/'progress.json'
target.write_text(json.dumps({'index':0,'payload':'x'*8192}),encoding='utf-8')
errors=[];reads=0
def replace():
    try:
        for index in range(1,251):
            temp=lab/'progress.tmp';temp.write_text(json.dumps({'index':index,'payload':'x'*8192}),encoding='utf-8')
            for attempt in range(20):
                try:os.replace(temp,target);break
                except OSError:
                    if attempt==19:raise
                    time.sleep(.01)
    except BaseException as e:errors.append(str(e))
writer=threading.Thread(target=replace);writer.start()
while writer.is_alive():
    value=json.loads(shared.read_shared_text(target));assert len(value['payload'])==8192;reads+=1
writer.join();assert not errors,errors
assert json.loads(shared.read_shared_text(target))['index']==250
pins=json.loads((qa/'candidate-pins.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(root/'native-camera-delivery/native-camera-qa-kit-task22.zip') as kit:
    assert json.loads(kit.read('candidate-pins.json'))==pins
    assert kit.read('shared_progress_io.py')==(qa/'shared_progress_io.py').read_bytes()
    assert 'RegisterAndRunOneShot.ps1' not in kit.namelist()
with zipfile.ZipFile(root/'native-camera-delivery'/pins['product']) as product:
    assert not any('HardwareWmiTimeout' in n or 'camera_native_qa' in n for n in product.namelist())
    camera=product.read('net/muxigame/terminal/client/TerminalCamera.class')
with zipfile.ZipFile(qa/'build'/pins['qa']) as candidate:
    assert 'HardwareWmiTimeoutQAMixin' in candidate.read('task22_camera_qa.mixins.json').decode()
    assert 'QA_HARDWARE_WMI_TIMEOUT_MS' in candidate.read('net/muxigame/terminal/qa/mixin/HardwareWmiTimeoutQAMixin.class').decode('latin1')
for name in ['src/main/java/net/muxigame/terminal/client/TerminalCamera.java','src/main/java/net/muxigame/terminal/client/TerminalCameraScreen.java']:
    assert '\ufffd' not in (root/'camera-native-worktree'/name).read_text(encoding='utf-8')
result={'success':True,'pythonSyntax':True,'atomicWindowsReplacements':250,'concurrentSharedReads':reads,'qaPinsMatchDelivery':True,'qaOnlyWmiMixinRegistered':True,'productContainsNoQaWmi':True,'productSha256':pins['productSha256'],'qaSha256':pins['qaSha256'],'realGameStarted':False,'unicodeSourceValid':True}
(qa/'build/input-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))
