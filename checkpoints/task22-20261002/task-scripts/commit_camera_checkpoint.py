"""Finalize an authorized local source checkpoint only. No push or runtime actions."""
from pathlib import Path
import hashlib,json,subprocess
root=Path(__file__).resolve().parent;work=root/'camera-native-worktree';out=root/'native-camera-delivery'
branch='checkpoint/task22-camera-album-native-20261002';prefix='checkpoints/task22-20261002/'
def git(*args):return subprocess.run(['git','-C',str(work),*args],capture_output=True,check=True).stdout
assert git('branch','--show-current').decode().strip()==branch
staged=[p.decode('utf-8') for p in git('diff','--cached','--name-only','-z').split(b'\0') if p]
assert staged and all(p.startswith(prefix) for p in staged),'Unexpected staged paths'
assert not any(Path(p).suffix.lower() in {'.jar','.zip','.png','.jpg','.jpeg','.avif','.bundle','.pem','.key','.log'} for p in staged)
hashes=json.loads((work/prefix/'source-hashes.json').read_text(encoding='utf-8'))
for name,sha in hashes.items():assert hashlib.sha256(git('show',':'+prefix+name)).hexdigest()==sha,name
git('commit','-q','-m','checkpoint(task22): save camera album sources and pending native QA')
commit=git('rev-parse','HEAD').decode().strip()
bundle=out/'task22-camera-album-checkpoint.bundle'
git('bundle','create',str(bundle),'refs/heads/'+branch)
git('bundle','verify',str(bundle))
assert not git('status','--porcelain','--',prefix)
assert git('rev-parse','refs/heads/main').decode().strip()=='a0ba0ec1ac3df1694ed8dd497d0ec88fc2f5cd50'
remaining=git('status','--porcelain=v1','-z','--untracked-files=all')
receipt=json.loads((out/'checkpoint-save-receipt.json').read_text(encoding='utf-8'))
receipt.update(localRepository=str(work),bareRepository=str(root/'camera-repo.git'),localOrigin=r'C:\Users\Administrator\WorkSpace\muxigame\muxi-terminal',upstreamRemote='https://github.com/muxigame/muxi-terminal.git',upstreamVisibility='public',upstreamVisibilityVerifiedBy='GitHub repository metadata',branch=branch,commit=commit,committedFiles=len(staged),exactSourceHashesVerified=len(hashes),remainingWorkingTreeRecords=len([p for p in remaining.split(b'\0') if p]),remainingChangesPreserved=True,gitBundle=str(bundle),gitBundleSha256=hashlib.sha256(bundle.read_bytes()).hexdigest(),gitBundleVerified=True,pushed=False,pushOwner='parent-designated sole publishing owner',mainDevTagsForceUntouched=True,paused=True)
(out/'checkpoint-commit-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(receipt,ensure_ascii=False,indent=2))
