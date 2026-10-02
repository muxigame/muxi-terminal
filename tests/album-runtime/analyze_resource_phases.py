import json,sys
from pathlib import Path
from collections import OrderedDict

lab=Path(sys.argv[1])
summary=json.loads((lab/'run-summary.json').read_text(encoding='utf-8')) if (lab/'run-summary.json').exists() else {}
samples=json.loads((lab/'process-memory.json').read_text(encoding='utf-8'))
main_pid=json.loads((lab/'pid.json').read_text(encoding='utf-8'))['pid']
groups=OrderedDict()
renderer_seen=False
for sample in samples:
    phase=sample['progress'].get('phase','functional')
    renderer_present=any(p['kind']=='renderer' for p in sample['processes'])
    if sample['progress']['stage']==99:phase='normal-logout'
    elif renderer_seen and not renderer_present and phase in ('warmup','loaded-hold','closed-hold','measured-block-1','measured-block-2','between-blocks-idle'):phase='renderer-missing/'+phase
    renderer_seen=renderer_seen or renderer_present
    groups.setdefault(phase,[]).append(sample)
def by_kind(sample):
    kinds={}
    for p in sample['processes']:
        kind='minecraft' if p['pid']==main_pid else (p.get('name','unclassified-child') if p['kind']=='minecraft-or-cef-browser' else p['kind'])
        kinds[kind]=kinds.get(kind,0)+p['private']
        if p['pid']!=main_pid and p['kind']!='minecraft-or-cef-browser':kinds['cef-child-total']=kinds.get('cef-child-total',0)+p['private']
    return kinds
phases=[]
for name,items in groups.items():
    first,last=by_kind(items[0]),by_kind(items[-1])
    kinds={k:{'firstMiB':round(first.get(k,0)/1048576,2),
              'lastMiB':round(last.get(k,0)/1048576,2),
              'deltaMiB':round((last.get(k,0)-first.get(k,0))/1048576,2),
              'rangeMiB':[round(min(by_kind(x).get(k,0) for x in items)/1048576,2),round(max(by_kind(x).get(k,0) for x in items)/1048576,2)]}
           for k in sorted(set(first)|set(last))}
    phases.append({'phase':name,'samples':len(items),'seconds':round((items[-1]['progress'].get('nanoTime',0)-items[0]['progress'].get('nanoTime',0))/1e9,2),'privateMemory':kinds})
resource_groups=OrderedDict()
for sample in summary.get('memorySamples',[]):
    if 'resources' in sample:resource_groups.setdefault(sample['phase'],[]).append(sample['resources'])
resource_phases=[]
fields=('heapUsed','naturalGcCount','nativeImagesLive','nativeImageBytesLive','cefTexturesOwned','cefTexturesLive','cefClients','bridgeQueriesOutstanding','directBufferBytes','cameraWorkerThreads')
for name,items in resource_groups.items():
    resource_phases.append({'phase':name,'samples':len(items),'resources':{k:{'first':items[0].get(k),'last':items[-1].get(k),'min':min(x[k] for x in items if k in x),'max':max(x[k] for x in items if k in x)} for k in fields if k in items[0]}})
log=(lab/'boot.log').read_text(encoding='utf-8',errors='replace')
out={'lab':str(lab),'runCompleted':bool(summary),'success':summary.get('success'),'cleanExit':summary.get('cleanExit'),'forcedGc':summary.get('forcedGc'),'productSha256':json.loads((lab/'inputs.json').read_text(encoding='utf-8'))['terminalSha256'],'finalizerMentions':log.count('CefQueryCallback_N::finalize')+log.count('CefQueryCallback_N.finalize'),'cefGpuUnexpectedExitLogs':log.count('GPU process exited unexpectedly'),'cefNetworkCrashLogs':log.count('Network service crashed'),'phases':phases,'resourcePhases':resource_phases,'backgroundResourcesBeforeTerminal':summary.get('backgroundResourcesBeforeTerminal'),'releasedResources':summary.get('releasedResources'),'warning':'Finite private generated world, fixed three photographs, one shader and builtin YSM; CEF child totals exclude console helpers and cannot isolate CEF browser allocations inside the JVM. Process private memory is not an allocator ownership proof.'}
(lab/'resource-analysis.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(out,ensure_ascii=False,indent=2))
