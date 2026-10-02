"""Save task22 source only. No build, test, SSH, deployment, or runtime actions."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile

root=Path(__file__).resolve().parent;work=root/'camera-native-worktree';delivery=root/'native-camera-delivery'
checkpoint=work/'checkpoints/task22-20261002';checkpoint.mkdir(parents=True,exist_ok=True)
manifest=json.loads((delivery/'manifest.json').read_text(encoding='utf-8'))
safe={'.java','.js','.cjs','.mjs','.py','.ps1','.css','.html','.toml','.json','.md','.txt','.patch'}
saved={}
def copy(source,target):
    if not source.is_file():return
    if source.suffix.lower() not in safe:raise RuntimeError('Not an approved source file: '+str(source))
    if source.stat().st_size>2_000_000:raise RuntimeError('Unexpected large source file: '+str(source))
    data=source.read_bytes()
    if any(line.strip() in (b'-----BEGIN PRIVATE KEY-----',b'-----BEGIN OPENSSH PRIVATE KEY-----') for line in data.splitlines()):raise RuntimeError('Private key blocked: '+str(source))
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    saved[target.relative_to(checkpoint).as_posix()]=hashlib.sha256(data).hexdigest()

owned=set(manifest['ownedModuleFiles']+manifest['testFiles'])
owned.update(['src/main/java/net/muxigame/terminal/client/'+name+'.java' for name in ['TerminalPhotoStore','TerminalPhotoImages','TerminalAlbumConfirmation']])
owned.update(['src/main/resources/assets/muxi_terminal/html/terminal/'+name for name in ['album-app.js','album-app.css','album-controller.js','camera-app.css','camera-controller.js']])
for directory in ['tests/camera','tests/album']:
    owned.update(p.relative_to(work).as_posix() for p in (work/directory).rglob('*') if p.is_file() and p.suffix.lower() in safe and 'build' not in p.relative_to(work/directory).parts and '__pycache__' not in p.parts)
for name in sorted(owned):copy(work/name,checkpoint/'current-source'/name)
for name in manifest['ownerWiringFiles']+['mod.json','build.py','dependencies.json']:copy(work/name,checkpoint/'owner-context'/name)
for name in ['native-camera-module.patch','native-camera-owner-wiring.patch','native-camera-tests.patch','native-camera-combined.patch','manifest.json','README.md','131-first-run-verdict.json']:
    copy(delivery/name,checkpoint/'native-review'/name)
for directory,names in [('delivery',['camera-module.patch','camera-owner-wiring.patch','camera-combined.patch','VERIFICATION.json','README.md']),('album-delivery',['album-module-incremental.patch','album-task6-owner-wiring.patch','album-combined-incremental.patch','VERIFICATION.json','README.md'])]:
    for name in names:copy(root/directory/name,checkpoint/'historical-review'/directory/name)
qa=root/'camera-native-qa'
for p in (qa/'java').rglob('*.java'):copy(p,checkpoint/'qa-source'/p.relative_to(qa))
for name in ['build-qa.py','run-camera-qa.py','shared_progress_io.py','qa_fml_config.py','NativeCameraSession2Fixed.ps1','HANDOFF.md','candidate-pins.json','NativeCameraEntry.ps1','RegisterAndRunOneShot.ps1','retry-prepare-131.py']:
    copy(qa/name,checkpoint/'qa-source'/name)
for p in root.glob('*.py'):copy(p,checkpoint/'task-scripts'/p.name)
for p in root.glob('*.ps1'):copy(p,checkpoint/'task-scripts'/p.name)
copy(root/'native-camera-readme.txt',checkpoint/'task-scripts/native-camera-readme.txt')
readme='''# Task22 source checkpoint — paused, not release-ready

This commit saves source snapshots and independently reviewable patches. It does not apply or claim ownership of the mixed task6/task14 working-tree baseline. Do not cherry-pick the entire mixed worktree or publish this candidate as accepted.

Current production source: current-source/. Shared owner wiring and compile context: owner-context/. Native module/wiring deltas: native-review/. Prior camera and album work: historical-review/. QA-only code and fixed Session2 action: qa-source/. Task scripts: task-scripts/.

The .2 candidate compiles; its real retest has not run. First actual 131 run, Java 15204, passed 45 assertions and saved six photos but failed at stage 5 with CefQueryCallback_N::finalize(). Exit 0 is normal process shutdown, not functional acceptance. All selfies only showed sky; YSM self-portrait acceptance remains pending. Current .2 repair completes invalid album callbacks with a safe rejection and has 69 bridge fixture checks. QA also adds stable scene readiness and authority/render diagnostics. No production changes, upload, OS camera, or new task registration.

Paused by user before switching computers. Do not execute tests, launchers, SSH scripts, builds, or deployments until the parent coordinates authorization on 131. The two older one-shot task scripts are historical code and were never registered; the current handoff exclusively uses task14's existing MuxiDesktopQA entry. Session2 fixed script is already staged on 131 for run-20261002-055245-420455d3, not started.

Photo PNGs, logs, runtime data, build outputs, archives, credentials, and binaries are deliberately outside this Git checkpoint. They remain in the local evidence/review archives. The original mixed worktree and other owners' changes are preserved untouched.
'''
(checkpoint/'README.md').write_text(readme,encoding='utf-8')
(checkpoint/'.gitignore').write_text('build/\nlogs/\n__pycache__/\n*.pyc\n*.jar\n*.zip\n*.png\n*.jpg\n*.jpeg\n*.avif\n*.bundle\n*.pem\n*.key\n.env*\n',encoding='utf-8')
(checkpoint/'.gitattributes').write_text('* -text\n',encoding='utf-8')
(checkpoint/'source-hashes.json').write_text(json.dumps(saved,indent=2),encoding='utf-8')
archive=delivery/'task22-source-checkpoint.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in checkpoint.rglob('*'):
        if p.is_file():z.write(p,p.relative_to(checkpoint).as_posix())

# Save all remaining mixed source as a LOCAL-ONLY context archive, without committing it.
context=delivery/'task22-uncommitted-source-context.zip';context_count=0
status=subprocess.run(['git','-C',str(work),'status','--porcelain=v1','-z','--untracked-files=all'],capture_output=True,check=True).stdout
with zipfile.ZipFile(context,'w',zipfile.ZIP_DEFLATED) as z:
    for entry in status.split(b'\0'):
        if not entry:continue
        name=entry[3:].decode('utf-8');p=work/name
        if name.startswith('checkpoints/') or not p.is_file():continue
        source_asset=name.startswith('src/main/resources/assets/') and '/textures/' in name and p.suffix.lower()=='.png' and p.stat().st_size<262144
        if (p.suffix.lower() in safe or source_asset) and p.stat().st_size<2_000_000:
            if any(part.lower() in {'build','logs','screenshots','__pycache__','.git','.aws','.codex','.agents'} for part in p.relative_to(work).parts):continue
            z.write(p,name);context_count+=1
    diff=subprocess.run(['git','-C',str(work),'diff','--no-ext-diff','--no-color'],capture_output=True,check=True).stdout
    z.writestr('mixed-working-tree.diff',diff)
result={'sourceCheckpoint':str(checkpoint),'sourceArchive':str(archive),'sourceArchiveSha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'sourceFiles':len(saved),'uncommittedContextArchive':str(context),'uncommittedContextSha256':hashlib.sha256(context.read_bytes()).hexdigest(),'contextFiles':context_count,'candidateProductSha256':manifest['product']['sha256'],'noTestsBuildsSshOrDeployments':True,'productionChanged':False}
(delivery/'checkpoint-save-receipt.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))
