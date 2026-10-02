"""Package ONLY task22 native-camera deltas against the frozen task14 owner snapshot."""
from pathlib import Path
import difflib,hashlib,json,shutil,subprocess,zipfile
root=Path(__file__).resolve().parent;work=root/'camera-native-worktree';base=root/'camera-native-baseline';out=root/'native-camera-delivery';out.mkdir(exist_ok=True)
java='src/main/java/net/muxigame/terminal/client/';web='src/main/resources/assets/muxi_terminal/html/terminal/'
module=[java+n for n in ['TerminalCamera.java','TerminalCameraScreen.java','TerminalCameraBridge.java','TerminalAlbumBridge.java','camera/mixin/NativeCameraHudMixin.java']]+[web+'camera-app.js','src/main/resources/muxi_terminal.camera.mixins.json']
wiring=[java+'TerminalClient.java',web+'app.js','src/main/resources/META-INF/neoforge.mods.toml']
tests=['tests/camera/native-entry.test.cjs','tests/camera/run-native-state.py','tests/album/run-bridge-fixtures.py']
def patch(files):
    text=''
    for name in files:
        a=base/name;b=work/name
        old=a.read_text(encoding='utf-8').splitlines(keepends=True) if a.exists() else []
        new=b.read_text(encoding='utf-8').splitlines(keepends=True)
        text+='diff --git a/'+name+' b/'+name+'\n'
        if not a.exists():text+='new file mode 100644\n'
        text+=''.join(difflib.unified_diff(old,new,fromfile='a/'+name if a.exists() else '/dev/null',tofile='b/'+name))
    return text
for name,files in [('native-camera-module.patch',module),('native-camera-owner-wiring.patch',wiring),('native-camera-tests.patch',tests),('native-camera-combined.patch',module+wiring+tests)]:
    (out/name).write_text(patch(files),encoding='utf-8')
meta=json.loads((work/'build/release.json').read_text(encoding='utf-8'));shutil.copy2(work/'build/libs'/meta['artifact'],out/meta['artifact']);shutil.copy2(work/'build/release.json',out/'candidate-release.json')
for source,name in [(work/'build/native-camera-tests/native-state-result.json','native-state-result.json'),(work/'build/album-tests/bridge-result.json','album-bridge-result.json'),(work/'build/album-tests/library-result.json','photo-library-result.json')]:
    if source.exists():shutil.copy2(source,out/name)
for source,name in [(root/'camera-native-qa/build/input-verification.json','qa-input-verification.json'),(root/'camera-repair/evidence/131-native-inputs-updated.json','131-prepared-inputs.json')]:
    if source.exists():shutil.copy2(source,out/name)
for source,name in [(root/'camera-repair/evidence/131-native-real/first-run-verdict.json','131-first-run-verdict.json'),(root/'camera-repair/evidence/131-native-retry-prepared.json','131-retry-prepared.json')]:
    if source.exists():shutil.copy2(source,out/name)
manifest={'status':'IMPLEMENTED_AND_COMPILED; REAL_131_ACCEPTANCE_PENDING; NOT_PUBLISHABLE_YET','baseline':'task14/unified-review/git-handoff-20261002/sources/muxi-terminal snapshot','product':meta,'ownedModuleFiles':module,'ownerWiringFiles':wiring,'testFiles':tests,'baselineSha256':{},'modifiedSha256':{},'productionChanged':False,'heavyClientOn008Started':False,'real131ClientStarted':False,'newTaskRegistered':False}
if (out/'131-first-run-verdict.json').exists():
    manifest.update(status='FIRST_REAL_131_RUN_FAILED; REPAIRED_CANDIDATE_RETEST_PENDING; NOT_PUBLISHABLE',real131ClientStarted=True,firstRealRun=json.loads((out/'131-first-run-verdict.json').read_text(encoding='utf-8')))
for name in module+wiring+tests:
    if (base/name).exists():manifest['baselineSha256'][name]=hashlib.sha256((base/name).read_bytes()).hexdigest()
    manifest['modifiedSha256'][name]=hashlib.sha256((work/name).read_bytes()).hexdigest()
(out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
readme=(root/'native-camera-readme.txt').read_text(encoding='utf-8')
(out/'README.md').write_text(readme,encoding='utf-8')
check=root/'native-camera-patch-check';check.mkdir(exist_ok=True)
for name in module+wiring+tests:
    if (base/name).exists():target=check/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(base/name,target)
subprocess.run(['git','init','--quiet',str(check)],check=True)
subprocess.run(['git','-C',str(check),'apply','--check',str(out/'native-camera-combined.patch')],check=True)
kit=out/'native-camera-qa-kit-task22.zip';qa=root/'camera-native-qa'
with zipfile.ZipFile(kit,'w',zipfile.ZIP_DEFLATED) as z:
    for name in ['run-camera-qa.py','qa_fml_config.py','shared_progress_io.py','NativeCameraSession2Fixed.ps1','HANDOFF.md','candidate-pins.json','build-qa.py']:z.write(qa/name,name)
    z.write(qa/'build/task22-camera-native-qa-only.jar','QA-ONLY/task22-camera-native-qa-only.jar')
    for p in (qa/'java').rglob('*.java'):z.write(p,p.relative_to(qa).as_posix())
bundle=out/'native-camera-task22-review.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    for p in out.iterdir():
        if p.is_file() and p!=bundle and (p.suffix!='.jar' or p.name==meta['artifact']):z.write(p,p.name)
print(json.dumps({'patchCheckPassed':True,'reviewBundle':str(bundle),'candidateSha256':meta['sha256'],'real131Acceptance':'PENDING'},indent=2))
