from pathlib import Path
import hashlib,json
from PIL import Image,ImageChops
root=Path(__file__).resolve().parent;folder=root/'camera-repair/evidence/131-native-real'
summary=json.loads((folder/'summary.json').read_text(encoding='utf-8'))
assert summary['exitCode']==0 and not summary['completed'] and summary['checks']==45
for name,sha in summary['artifactSha256'].items():assert hashlib.sha256((folder/name).read_bytes()).hexdigest()==sha,name
photos=summary['photos'];assert len({p['id'] for p in photos})==6
comparisons=[]
for index,photo in enumerate(photos):
    mode=photo['mode'];raw=Image.open(folder/photo['artifact']).convert('RGB');ui=Image.open(folder/f'shot-{index:02d}-{mode}-ui.png').convert('RGB')
    assert raw.size==ui.size==(1280,720)
    diff=ImageChops.difference(raw,ui)
    # World-only center excludes every native control and viewfinder corner.
    center=diff.crop((160,140,1120,540))
    assert center.getbbox() is None,'World pixels changed between readback and same-frame UI capture'
    changed=sum(pixel!=(0,0,0) for pixel in diff.getdata())
    assert changed>1000,'Missing real UI/control comparison'
    comparisons.append({'photo':photo['artifact'],'sameFrameWorldCenterExactMatch':True,'uiDifferencePixels':changed,'dimensions':[1280,720]})
verdict={'acceptance':'FAILED_AT_REAL_CEF_ALBUM; RETEST_REQUIRED','privatePid':15204,'session':2,'productSha256':summary['productSha256'],'qaSha256':summary['qaSha256'],'realChecksPassed':45,'photos':6,'uniqueNames':True,'rawPngHashesMatchRemote':True,'shaderPackInUse':summary['shaderPackInUse'],'gpuRenderer':summary['gpuRenderer'],'normalExitCode':0,'photoUiComparisons':comparisons,'visualReview':'Native UI contains only MC buttons, viewfinder corners and local status. Raw PNGs contain no native controls or ordinary HUD. All selfies only show sky: YSM self-portrait acceptance NOT passed; initial world/profile synchronization was still running during capture.','failure':summary['error'],'notExecuted':['album viewer/recycle/restore','album same-viewer camera return','F1 initially hidden HUD capture','physical focus loss/regain','cancel pending readback','explicit disconnect restoration/resource checks']}
(folder/'first-run-verdict.json').write_text(json.dumps(verdict,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in verdict.items() if k!='photoUiComparisons'},ensure_ascii=False,indent=2))
