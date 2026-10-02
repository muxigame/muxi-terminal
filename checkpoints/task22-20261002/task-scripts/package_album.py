from pathlib import Path
import difflib,hashlib,json,shutil,subprocess,zipfile
root=Path(__file__).resolve().parent;baseline=root/'album-baseline';work=root/'album-worktree';delivery=root/'album-delivery';delivery.mkdir(exist_ok=True)
task6=Path('C:/Users/Administrator/Documents/Codex/2026-10-01/task-6/sso-integration/muxi-terminal')
before={p.relative_to(baseline).as_posix():p for p in (baseline/'src').rglob('*') if p.is_file()}
after={p.relative_to(work).as_posix():p for p in (work/'src').rglob('*') if p.is_file()}
after.update({p.relative_to(work).as_posix():p for p in (work/'tests/album').glob('*') if p.is_file() and p.suffix in {'.java','.py','.cjs','.mjs'}})
modified=[n for n in before if before[n].read_bytes()!=after[n].read_bytes()]
shared=['src/main/java/net/muxigame/terminal/client/TerminalBrowserSession.java','src/main/java/net/muxigame/terminal/client/TerminalNativeBridge.java','src/main/resources/assets/muxi_terminal/html/terminal/app.js','src/main/resources/assets/muxi_terminal/html/terminal/index.html']
owned=['src/main/java/net/muxigame/terminal/client/TerminalPhotoStore.java','src/main/java/net/muxigame/terminal/client/TerminalCameraBridge.java','src/main/resources/assets/muxi_terminal/html/terminal/camera-app.js']
assert sorted(modified)==sorted(shared+owned),modified
new=sorted(set(after)-set(before))
assert all(n.startswith('tests/album/') or n.endswith(('TerminalAlbumBridge.java','TerminalAlbumConfirmation.java','TerminalPhotoImages.java','album-app.js','album-controller.js','album-app.css')) for n in new)
def patch(paths):
    output=[]
    for n in sorted(paths):
        a=before[n].read_text(encoding='utf-8') if n in before else '';b=after[n].read_text(encoding='utf-8')
        output.append(f'diff --git a/{n} b/{n}\n')
        if n not in before:output.append('new file mode 100644\n')
        output.extend(difflib.unified_diff(a.splitlines(keepends=True),b.splitlines(keepends=True),fromfile='a/'+n if n in before else '/dev/null',tofile='b/'+n))
    return ''.join(output)
for name,paths in [('album-module-incremental.patch',new+owned),('album-task6-owner-wiring.patch',shared),('album-combined-incremental.patch',new+modified)]:
    (delivery/name).write_text(patch(paths),encoding='utf-8',newline='\n')
check=root/'album-patch-check'
for n in new:
    p=check/n;assert p.resolve().is_relative_to(check.resolve())
    if p.is_file():p.unlink()
shutil.copytree(baseline/'src',check/'src',dirs_exist_ok=True);shutil.copytree(baseline/'tests',check/'tests',dirs_exist_ok=True)
for filename in ['album-module-incremental.patch','album-task6-owner-wiring.patch']:
    subprocess.run(['git','apply','--check',str(delivery/filename)],cwd=check,check=True)
    subprocess.run(['git','apply','--whitespace=error',str(delivery/filename)],cwd=check,check=True)
for n,p in after.items():assert (check/n).read_text(encoding='utf-8')==p.read_text(encoding='utf-8') if n in new+modified else (check/n).read_bytes()==p.read_bytes(),n
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
drift=[n for n,p in before.items() if not (task6/n).is_file() or (task6/n).read_bytes()!=p.read_bytes()]
review=delivery/'muxi-terminal-album-0.2.2-review.jar';shutil.copy2(work/'build/libs/muxi-terminal-0.2.2.jar',review)
evidence=delivery/'evidence';evidence.mkdir(exist_ok=True)
for p in (work/'tests/album/evidence').glob('*'):
    if p.is_file():shutil.copy2(p,evidence/p.name)
for n in ['library-result.json','bridge-result.json']:shutil.copy2(work/'build/album-tests'/n,evidence/n)
shutil.copy2(work/'build/release.json',evidence/'build-release.json')
shutil.copy2(work/'build/integration-evidence/navigation-adapter-tests.log',evidence/'sso-adapter.log')
shutil.copy2(work/'build/motion-tests/result.json',evidence/'motion-result.json')
shutil.copy2(work/'tests/camera/evidence/results.json',evidence/'camera-ui-result.json')
controller=subprocess.run(['node',str(work/'tests/album/controller.test.cjs')],capture_output=True,text=True,check=True)
(evidence/'album-controller-result.json').write_text(controller.stdout,encoding='utf-8')
manifest={'owner':'task6','sourceBaseline':str(task6),'worktree':str(work),'branch':'task22-album','patchBase':'current task6 integrated 0.2.2 candidate, already containing original task22 camera/settings/music/games/SSO/input',
 'oldCameraDeliveryPreserved':True,'task6SourceDriftSinceSnapshot':drift,'newFiles':new,'ownedModifiedFiles':owned,'sharedWiringFiles':shared,
 'baseHashes':{n:sha(before[n]) for n in modified},'resultHashes':{n:sha(after[n]) for n in new+modified},'otherSourceBytesUnchanged':True,'separatePatchApplyAndResultEquality':True,
 'reviewJar':{'name':review.name,'sha256':sha(review),'bytes':review.stat().st_size,'published':False},
 'checks':{'albumController':json.loads(controller.stdout),'albumBrowser':json.loads((evidence/'results.json').read_text()),'photoLibrary':json.loads((evidence/'library-result.json').read_text()),'nativeBridgeFixture':json.loads((evidence/'bridge-result.json').read_text()),'existingCameraController':14,'existingCameraBrowser':13,'existingSsoAdapter':84,'existingContentTransition':20,'fullOfflineCompilation':True},
 'photoTestsScope':'synthetic temporary libraries only; no real user photos enumerated, read or moved',
 'realGameValidation':{'run':False,'blocker':'GUI channel unavailable; parent must coordinate exclusive 131 slot with task14 before any client/GUI start'}}
(delivery/'VERIFICATION.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
with zipfile.ZipFile(delivery/'album-task22-incremental-review.zip','w',zipfile.ZIP_DEFLATED) as z:
    for n in new+modified:z.write(after[n],'source/'+n)
    for p in delivery.rglob('*'):
        if p.is_file() and p.suffix!='.zip':z.write(p,p.relative_to(delivery).as_posix())
print(json.dumps({'success':True,'newFiles':len(new),'moduleModifiedFiles':len(owned),'sharedWiringFiles':len(shared),'sourceDrift':drift,'jarSHA256':sha(review)},ensure_ascii=False))
