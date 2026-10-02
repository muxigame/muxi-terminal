"""Package camera-only delta against task6's captured unified source; never shared Git state."""
from pathlib import Path
import difflib,hashlib,json,shutil,subprocess,zipfile

root=Path(__file__).resolve().parent
baseline=root/'baseline';work=root/'camera-worktree';delivery=root/'delivery';delivery.mkdir(exist_ok=True)
before={p.relative_to(baseline).as_posix():p for p in (baseline/'src').rglob('*') if p.is_file()}
after={p.relative_to(work).as_posix():p for p in (work/'src').rglob('*') if p.is_file()}
tests={p.relative_to(work).as_posix():p for p in (work/'tests/camera').glob('*') if p.is_file() and p.suffix in {'.java','.py','.cjs','.mjs'}}
after.update(tests)
changed=[p for p in before if before[p].read_bytes()!=after[p].read_bytes()]
expected=['src/main/java/net/muxigame/terminal/client/TerminalBrowserSession.java','src/main/java/net/muxigame/terminal/client/TerminalNativeBridge.java','src/main/resources/assets/muxi_terminal/html/terminal/app.js','src/main/resources/assets/muxi_terminal/html/terminal/index.html']
assert sorted(changed)==sorted(expected),changed
new=sorted(set(after)-set(before))
assert all('/camera' in p.lower() or p.endswith(('TerminalCamera.java','TerminalCameraBridge.java','TerminalPhotoStore.java')) for p in new),new
def patch(paths):
    output=[]
    for path in sorted(paths):
        a=before[path].read_text(encoding='utf-8') if path in before else ''
        b=after[path].read_text(encoding='utf-8')
        output.append(f'diff --git a/{path} b/{path}\n')
        if path not in before:output.append('new file mode 100644\n')
        output.extend(difflib.unified_diff(a.splitlines(keepends=True),b.splitlines(keepends=True),fromfile='a/'+path if path in before else '/dev/null',tofile='b/'+path))
    return ''.join(output)
for name,paths in [('camera-module.patch',new),('camera-owner-wiring.patch',changed),('camera-combined.patch',new+changed)]:
    (delivery/name).write_text(patch(paths),encoding='utf-8',newline='\n')
check=root/'patch-check'
for name in new:
    p=check/name
    assert p.resolve().is_relative_to(check.resolve())
    if p.is_file():p.unlink()
shutil.copytree(baseline/'src',check/'src',dirs_exist_ok=True);shutil.copytree(baseline/'tests',check/'tests',dirs_exist_ok=True)
subprocess.run(['git','apply','--check',str(delivery/'camera-combined.patch')],cwd=check,check=True)
subprocess.run(['git','apply','--whitespace=error',str(delivery/'camera-combined.patch')],cwd=check,check=True)
for name,p in after.items():
    assert (check/name).read_text(encoding='utf-8')==p.read_text(encoding='utf-8') if name in new+changed else (check/name).read_bytes()==p.read_bytes(),name
review=delivery/'muxi-terminal-camera-0.2.1-review.jar';shutil.copy2(work/'build/libs/muxi-terminal-0.2.1.jar',review)
evidence=delivery/'evidence';evidence.mkdir(exist_ok=True)
for p in (work/'tests/camera/evidence').glob('*'):
    if p.is_file():shutil.copy2(p,evidence/p.name)
shutil.copy2(work/'build/camera-tests/photo-store-result.json',evidence/'photo-store-result.json')
shutil.copy2(work/'build/motion-tests/result.json',evidence/'motion-result.json')
shutil.copy2(work/'build/release.json',evidence/'build-release.json')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
manifest={'owner':'task6','worktree':str(work),'worktreeBranch':'task22-camera','checkoutHead':'a0ba0ec','patchBase':'task6 unified-terminal snapshot, NOT the shared main checkout',
    'newFiles':new,'wiringFiles':changed,'baseHashes':{n:sha(before[n]) for n in changed},'resultHashes':{n:sha(after[n]) for n in new+changed},
    'unchangedSharedSourceVerified':True,'cleanPatchApplyAndSourceEquality':True,'reviewJar':{'name':review.name,'sha256':sha(review),'bytes':review.stat().st_size,'published':False},
    'checks':{'controller':14,'browserSynthetic':json.loads((evidence/'results.json').read_text())['passed'],'concurrentPhotoSaves':80,'photoStore':json.loads((evidence/'photo-store-result.json').read_text()),'ssoAdapter':84,'nativeContentTransition':20,'offlineJavaCompilation':True,'javascriptSyntax':True},
    'realGameValidation':{'run':False,'blocker':'131 GUI channel absent; parent must coordinate task14 slot. No heavy client started on 008 or 131.'}}
(delivery/'VERIFICATION.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
with zipfile.ZipFile(delivery/'camera-task22-review.zip','w',zipfile.ZIP_DEFLATED) as z:
    for path in new+changed:z.write(after[path],'source/'+path)
    for p in delivery.rglob('*'):
        if p.is_file() and p.suffix!='.zip':z.write(p,p.relative_to(delivery).as_posix())
print(json.dumps({'success':True,'delivery':str(delivery),'newFiles':len(new),'wiringFiles':len(changed),'jarSha256':sha(review)},ensure_ascii=False))
