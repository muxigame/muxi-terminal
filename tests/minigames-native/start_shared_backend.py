"""One shared Auth/Web fixture for real A/B clients; never launches Minecraft."""
from pathlib import Path
import hashlib,json,os,subprocess,tempfile,time
from datetime import datetime,timezone
import urllib.request,ssl

HERE=Path(__file__).resolve().parent
REPO=HERE.parent.parent
WEB=REPO.parent/'better-mc-remake'
WORK=Path(r'C:\Users\ranzh\workspace\dev\muxigame')
PYTHON=Path(r'C:\Users\ranzh\Documents\Codex\terminal-sso-dedicated-task4-20261002\python\python.exe')
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
OWNER=REPO/'build/minigames-native/shared-backend-owner.json'

def main():
    if OWNER.exists():
        previous=json.loads(OWNER.read_text(encoding='utf-8'))
        private=Path(previous['privateReadyPath'])
        if private.exists():
            value=json.loads(private.read_text(encoding='utf-8'))
            try:
                with urllib.request.urlopen(value['auth_url']+'/healthz',context=ssl.create_default_context(cafile=value['ca_file']),timeout=3) as response:
                    if response.status==200:raise RuntimeError('Shared backend already alive; reuse owner record, do not start a second instance')
            except (OSError,ValueError):pass
    home=Path(tempfile.gettempdir())/('mgshared-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
    home.mkdir();private=home/'backend-ready.json'
    entry=WEB/'tests/run_terminal_mcef_backend.py'
    env={k:v for k,v in os.environ.items() if not k.startswith(('BMC_','MUXI_'))}
    env.update(BMC_SKIP_DOTENV='1',PYTHONIOENCODING='utf-8')
    with (home/'backend.log').open('w',encoding='utf-8') as output:
        process=subprocess.Popen([str(PYTHON),'-B',str(WEB/'tests/bootstrap_python_runtime.py'),str(entry),
            '--java-home',str(JDK),'--gson',str(WORK/'bmc5server/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'),
            '--trusted-source',str(REPO.parent/'muxi-minigames/src/main/java/net/muxigame/minigames/TrustedAccounts.java'),
            '--report',str(home/'backend-result.json'),'--ready',str(private),'--native','--shared-games','--lifetime-seconds','3600'],
            cwd=home,env=env,stdin=subprocess.DEVNULL,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    owner={'pid':process.pid,'home':str(home),'privateReadyPath':str(private),'publicReadyPath':str(home/'shared-backend-public.json'),
        'entry':str(entry),'entrySha256':hashlib.sha256(entry.read_bytes()).hexdigest(),'minecraftStarted':False,'productionMutation':False,
        'startedUTC':datetime.now(timezone.utc).isoformat()}
    OWNER.parent.mkdir(parents=True,exist_ok=True)
    OWNER.write_text(json.dumps(owner,indent=2),encoding='utf-8')
    deadline=time.monotonic()+60
    while not (home/'shared-backend-public.json').exists():
        if process.poll() is not None:raise RuntimeError('Shared backend exited; inspect owned backend.log')
        if time.monotonic()>deadline:raise TimeoutError('Shared backend not ready; inspect owned backend.log')
        time.sleep(.1)
    public=json.loads((home/'shared-backend-public.json').read_text(encoding='utf-8'))
    print(json.dumps({'owner':str(OWNER),'pid':process.pid,'public':public},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
