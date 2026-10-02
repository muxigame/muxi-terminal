using System.Diagnostics;
using System.Net;
using System.Reflection;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json.Nodes;
using BatterMC.Core;
using BatterMC.Launcher;

// Own QA coordinator, linked to actual RpcHost and the existing SSO issuer transport.
// Backend account tokens stay here; only native broker environment reaches the game.
var config=JsonNode.Parse(await File.ReadAllTextAsync(args[0]))!.AsObject();
var index=config["accountIndex"]!.GetValue<int>();
if(index is <0 or >1)throw new InvalidOperationException("Invalid isolated account selector");
var controlSecret=Environment.GetEnvironmentVariable("MUXI_LAUNCHER_QA_CONTROL")??throw new InvalidOperationException("Launcher capability absent");
using var controller=new HttpClient{Timeout=TimeSpan.FromSeconds(8)};
var paths=LauncherPaths.At(config["launcherState"]!.GetValue<string>());paths.EnsureDataDir();
using var host=new RpcHost(paths,new LauncherSettings(),new LocalState());
long uid=0;int rotations=0;
using var transport=new ActualIssuerTransport(config["authURL"]!.GetValue<string>(),config["siteURL"]!.GetValue<string>(),config["spki"]!.GetValue<string>(),async tokens=>{
    rotations++;await Control("launcher-rotated",new JsonObject{["uid"]=uid,["access_token"]=tokens["access_token"]!.GetValue<string>(),["refresh_token"]=tokens["refresh_token"]!.GetValue<string>()});
});
Field("_accountHttp").SetValue(host,new HttpClient(transport,false));
var seed=await Control("launcher-session",new JsonObject());uid=seed["uid"]!.GetValue<long>();
lock(Field("_accountSessionLock").GetValue(host)!){
    Field("_terminalAccountGeneration").SetValue(host,(long)Field("_terminalAccountGeneration").GetValue(host)!+1);
    Field("_account").SetValue(host,null);Field("_player").SetValue(host,null);
    Field("_accountToken").SetValue(host,seed["access_token"]!.GetValue<string>());
    Field("_accountRefreshToken").SetValue(host,seed["refresh_token"]!.GetValue<string>());
    Field("_lastRefresh").SetValue(host,default(DateTimeOffset));
}
await (Task)Method("LoadAccountAsync").Invoke(host,[])!;
Method("PersistAccountSession").Invoke(host,[]);
var credentials=new List<string>();
await using var broker=(TerminalCredentialBroker)Method("CreateTerminalCredentialBroker").Invoke(host,[uid.ToString(System.Globalization.CultureInfo.InvariantCulture),credentials])!;
var initial=(string?)await (Task<string?>)Method("MintTerminalCredentialAsync").Invoke(host,[CancellationToken.None])!;
if(!TerminalCredentialEnvironment.Valid(initial))throw new InvalidOperationException("Actual issuer bootstrap failed");
try{
    if(args.Contains("--probe-only")){
        using var pipe=new System.IO.Pipes.NamedPipeClientStream(".",broker.PipeName,System.IO.Pipes.PipeDirection.InOut,System.IO.Pipes.PipeOptions.Asynchronous);
        await pipe.ConnectAsync(5000);await pipe.WriteAsync(Encoding.ASCII.GetBytes(broker.Secret+"\n"));await pipe.FlushAsync();
        using var reader=new StreamReader(pipe,Encoding.ASCII);var fresh=await reader.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(6));
        if(!TerminalCredentialEnvironment.Valid(fresh))throw new InvalidOperationException("Actual broker credential absent");
        using var actual=new HttpClient(transport,false);
        using var proof=new HttpRequestMessage(HttpMethod.Post,"https://account.muxigame.com/api/launcher/minecraft/terminal-proof"){
            Content=new StringContent(new JsonObject{["challenge"]=new string('A',43),["requestId"]=Guid.NewGuid().ToString()}.ToJsonString(),Encoding.UTF8,"application/json")};
        proof.Headers.Authorization=new("MuxiTerminal",fresh);
        using var response=await actual.SendAsync(proof);if(response.StatusCode!=HttpStatusCode.OK)throw new InvalidOperationException("Actual issuer rejected broker proof");
        var stored=AccountStore.Load(paths.AccountFile);
        if(stored?.AccessToken!=(string?)Field("_accountToken").GetValue(host))throw new InvalidOperationException("DPAPI account persistence differs");
        await Report(new JsonObject{["success"]=true,["uid"]=uid,["accountIndex"]=index,["actualRpcHost"]=true,["actualBroker"]=true,["actualDPAPI"]=true,["actualIssuerProof"]=true,["minecraftStarted"]=false,["joinGrantMinted"]=false});
        Console.WriteLine($"SHARED_LAUNCHER_PROBE_PASS uid={uid} index={index}");return 0;
    }
    // Explicit GO is sent only after the coordinator verifies available client slots.
    if(await Console.In.ReadLineAsync()!="GO")throw new InvalidOperationException("Client capacity was not granted");
    var start=new ProcessStartInfo(config["java"]!.GetValue<string>()){UseShellExecute=false,CreateNoWindow=true,WorkingDirectory=config["gameDir"]!.GetValue<string>(),RedirectStandardOutput=true,RedirectStandardError=true};
    start.ArgumentList.Add("@"+config["argFile"]!.GetValue<string>());
    foreach(var key in start.Environment.Keys.Where(k=>k.StartsWith("MUXI_")).ToArray())start.Environment.Remove(key);
    TerminalCredentialEnvironment.Apply(start,initial,broker.PipeName,broker.Secret);
    using var game=Process.Start(start)??throw new InvalidOperationException("Real game did not start");
    using var gameLog=TextWriter.Synchronized(new StreamWriter(Path.Combine(config["gameDir"]!.GetValue<string>(),"game-boot.log"),false,Encoding.UTF8){AutoFlush=true});
    async Task Pump(StreamReader source){while(await source.ReadLineAsync() is {} line)await gameLog.WriteLineAsync(line);}
    var stdout=Pump(game.StandardOutput);var stderr=Pump(game.StandardError);
    Console.WriteLine($"SHARED_LAUNCHER_GAME_STARTED uid={uid} gamePid={game.Id}");
    var coordinator=config["coordinator"]!.GetValue<string>();var role=config["role"]!.GetValue<string>();
    var mintRequest=Path.Combine(coordinator,"mint-join-"+role+".json");var mintReceipt=Path.Combine(coordinator,"join-minted-"+role+".json");
    while(!game.HasExited){
        if(File.Exists(mintRequest)&&!File.Exists(mintReceipt)){
            await (Task)Method("MintJoinGrantAsync").Invoke(host,[CancellationToken.None])!;
            await File.WriteAllTextAsync(mintReceipt,new JsonObject{["uid"]=uid,["actualRpcHostMintInvoked"]=true,["mintedUTC"]=DateTimeOffset.UtcNow.ToString("O"),["ttlSeconds"]=180}.ToJsonString());
        }
        await Task.Delay(100);
    }
    await Task.WhenAll(stdout,stderr);
    await Report(new JsonObject{["uid"]=uid,["accountIndex"]=index,["actualRpcHost"]=true,["actualBroker"]=true,["actualDPAPI"]=true,["gamePid"]=game.Id,["gameExitCode"]=game.ExitCode,["joinGrantMintRequested"]=File.Exists(mintReceipt)});
    return game.ExitCode;
}finally{await (Task)Method("RevokeTerminalCredentialsAsync").Invoke(host,[])!;}

FieldInfo Field(string name)=>typeof(RpcHost).GetField(name,BindingFlags.Instance|BindingFlags.NonPublic)!;
MethodInfo Method(string name)=>typeof(RpcHost).GetMethod(name,BindingFlags.Instance|BindingFlags.NonPublic)!;
async Task Report(JsonObject value){value["productionMutation"]=false;await File.WriteAllTextAsync(config["report"]!.GetValue<string>(),value.ToJsonString());}
async Task<JsonObject> Control(string action,JsonObject value){
    value["action"]=action;value["accountIndex"]=index;
    using var request=new HttpRequestMessage(HttpMethod.Post,config["controller"]!.GetValue<string>()){Content=new StringContent(value.ToJsonString(),Encoding.UTF8,"application/json")};
    request.Headers.Add("X-Muxi-Launcher-QA-Control",controlSecret);
    using var response=await controller.SendAsync(request);response.EnsureSuccessStatusCode();return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
}
