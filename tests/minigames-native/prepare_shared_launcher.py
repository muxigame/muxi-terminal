"""Build/probe actual RpcHost A/B sessions against the one owned shared backend."""
from pathlib import Path
import hashlib,json,os,subprocess,concurrent.futures
from datetime import datetime,timezone
from xml.sax.saxutils import escape
from start_shared_backend import HERE,REPO,WEB,OWNER

DOTNET=Path(r'C:\Users\ranzh\Documents\Codex\2026-10-02\task-3\tools\dotnet\dotnet.exe')
def main():
    owner=json.loads(OWNER.read_text(encoding='utf-8'));home=Path(owner['home'])
    backend=json.loads(Path(owner['privateReadyPath']).read_text(encoding='utf-8'))
    build=home/('native-launcher-'+datetime.now(timezone.utc).strftime('%H%M%S'));build.mkdir(exist_ok=False)
    existing=WEB/'tests/terminal-native-launcher/Program.cs'
    text=existing.read_text(encoding='utf-8')
    transport='using System.Net;\nusing System.Security.Cryptography;\nusing System.Security.Cryptography.X509Certificates;\nusing System.Text.Json.Nodes;\n'+text[text.index('sealed class ActualIssuerTransport:'):]
    (build/'ExistingIssuerTransport.cs').write_text(transport,encoding='utf-8')
    project=build/'SharedGamesLauncherQA.csproj'
    xml='''<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net9.0-windows</TargetFramework><ImplicitUsings>enable</ImplicitUsings><Nullable>enable</Nullable><EnableDefaultCompileItems>false</EnableDefaultCompileItems></PropertyGroup><ItemGroup>'''
    for path in [HERE/'launcher/Program.cs',build/'ExistingIssuerTransport.cs',WEB/'client/sidecar/RpcHost.cs']:
        xml+='<Compile Include="'+escape(path.as_posix(),{'"':'&quot;'})+'"/>'
    for path in [WEB/'client/core/BatterMC.Core.csproj',WEB/'client/protocol/BatterMC.Protocol.csproj']:
        xml+='<ProjectReference Include="'+escape(path.as_posix(),{'"':'&quot;'})+'"/>'
    project.write_text(xml+'</ItemGroup></Project>',encoding='utf-8')
    env={k:v for k,v in os.environ.items() if not k.startswith(('BMC_','MUXI_'))}
    env.update(DOTNET_CLI_TELEMETRY_OPTOUT='1',DOTNET_NOLOGO='1')
    with (build/'build.log').open('w',encoding='utf-8') as log:
        result=subprocess.run([str(DOTNET),'build',str(project),'--ignore-failed-sources','--nologo','-v','minimal'],env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise RuntimeError('Shared launcher build failed; inspect owned build.log')
    dll=build/'bin/Debug/net9.0-windows/SharedGamesLauncherQA.dll'
    configs=[]
    for index,role in enumerate(['host','guest']):
        root=home/('launcher-'+role+'-'+build.name);root.mkdir()
        config={'controller':backend['url'],'authURL':backend['auth_url'],'siteURL':backend['site_url'],'spki':backend['spki'],
            'accountIndex':index,'role':role,'launcherState':str(root/'fresh-dpapi-state'),'report':str(root/'probe-result.json')}
        path=root/'public-config.json';path.write_text(json.dumps(config),encoding='utf-8');configs.append(path)
    def probe(path):
        local=dict(env);local['MUXI_LAUNCHER_QA_CONTROL']=backend['launcher_capability']
        with (path.parent/'probe.log').open('w',encoding='utf-8') as log:
            result=subprocess.run([str(DOTNET),str(dll),str(path),'--probe-only'],cwd=path.parent,env=local,stdout=log,stderr=subprocess.STDOUT,timeout=60)
        if result.returncode:raise RuntimeError('Actual broker probe failed; inspect owned '+str(path.parent/'probe.log'))
        return json.loads((path.parent/'probe-result.json').read_text(encoding='utf-8'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(probe,configs))
    receipt={'success':all(x.get('success') for x in results),'probes':results,'launcherDll':str(dll),
        'launcherSha256':hashlib.sha256(dll.read_bytes()).hexdigest(),'existingTransportSource':str(existing),
        'existingTransportSourceSha256':hashlib.sha256(existing.read_bytes()).hexdigest(),
        'minecraftStarted':False,'joinGrantsMinted':False,'productionMutation':False}
    (home/'shared-launcher-preparation.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    print(json.dumps(receipt,indent=2))

if __name__=='__main__':main()
