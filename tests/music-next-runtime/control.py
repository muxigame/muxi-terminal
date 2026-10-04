from pathlib import Path
import sys,json,time,subprocess,uuid
import argparse
parser=argparse.ArgumentParser(description="Bounded music-only acceptance on an already running isolated QA instance")
parser.add_argument('--instance-root',type=Path,required=True)
parser.add_argument('--debug-cli',type=Path,required=True)
parser.add_argument('--assets',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--player',default='MusicNext131')
args=parser.parse_args()
OWN=args.output.resolve(); OWN.mkdir(parents=True,exist_ok=True)
LAB=args.instance_root.resolve()
DEBUG=args.debug_cli.resolve()
PLAYER=args.player
ASSETS=args.assets
HOST=LAB/'host'
def qa(type,**args):
    ident=time.time_ns();request={'id':ident,'type':type,**args};target=HOST/'music-qa-request.json';tmp=target.with_suffix('.tmp')
    tmp.write_text(json.dumps(request),encoding='utf8')
    until=time.monotonic()+2
    while True:
        try:tmp.replace(target);break
        except PermissionError:
            if time.monotonic()>=until:raise
            time.sleep(.05)
    reply=HOST/f'music-qa-result-{ident}.json';until=time.monotonic()+30
    while time.monotonic()<until:
        if reply.exists():
            try:result=json.loads(reply.read_text(encoding='utf8'))
            except (OSError,ValueError):time.sleep(.05);continue
            if not result.get('ok'):raise RuntimeError(result)
            return result['state']
        time.sleep(.05)
    raise TimeoutError(request)
def native(role,type,**args):
    file=OWN/('native-command-'+uuid.uuid4().hex+'.json');file.write_text(json.dumps({'type':type,**args}),encoding='utf8')
    result=subprocess.run([sys.executable,str(DEBUG),'command','--instance-root',str(LAB),'--role',role,'--json-file',str(file)],capture_output=True,text=True,encoding='utf8',timeout=40)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    return json.loads(result.stdout.strip().splitlines()[-1])
def portable_fixture():
    ident=time.time_ns();server=LAB/'server';request={'id':ident,'player':PLAYER,'files':[(HOST/f'fixture-{n}.wav').as_uri() for n in range(2)]}
    target=server/'music-qa-request.json';tmp=target.with_suffix('.tmp');tmp.write_text(json.dumps(request),encoding='utf8')
    until=time.monotonic()+2
    while True:
        try:tmp.replace(target);break
        except PermissionError:
            if time.monotonic()>=until:raise
            time.sleep(.05)
    response=server/f'music-qa-result-{ident}.json';until=time.monotonic()+30
    while time.monotonic()<until:
        if response.exists():
            result=json.loads(response.read_text(encoding='utf8'))
            if not result.get('ok'):raise RuntimeError(result)
            return result
        time.sleep(.05)
    raise TimeoutError('server native portable fixture')
def wait(predicate,seconds=20):
    until=time.monotonic()+seconds;last=None
    while time.monotonic()<until:
        last=qa('observe')
        if predicate(last):return last
        time.sleep(.15)
    raise AssertionError(last)
