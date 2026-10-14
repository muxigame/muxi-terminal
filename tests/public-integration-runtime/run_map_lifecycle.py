"""Map bridge lifecycle fixtures; no Minecraft process or network is started."""
from pathlib import Path
import argparse,hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jdk',required=True,type=Path,help='Installed JDK 21 directory')
parser.add_argument('--source',type=Path,default=ROOT/'src/main/java/net/muxigame/terminal/client/TerminalNativeMapBridge.java')
parser.add_argument('--output',type=Path,default=ROOT/'build/map-lifecycle-fixtures')
args=parser.parse_args()
SOURCE=args.source.resolve();JDK=args.jdk.resolve();OUT=args.output.resolve()
if not SOURCE.is_file(): parser.error('Map bridge source does not exist')
if not (JDK/'bin/javac.exe').is_file() or not (JDK/'bin/java.exe').is_file(): parser.error('Installed Windows JDK required')
OUT.mkdir(parents=True,exist_ok=True)
files={
'org/cef/callback/CefQueryCallback.java':'package org.cef.callback;public interface CefQueryCallback{void success(String value);void failure(int code,String message);}',
'org/cef/browser/CefFrame.java':'package org.cef.browser;public class CefFrame{public String url;public long id=1;public boolean main=true,disposed;public long getIdentifier(){if(disposed)throw new IllegalStateException("disposed");return id;}public String getURL(){if(disposed)throw new IllegalStateException("disposed");return url;}public void dispose(){disposed=true;}}',
'org/cef/browser/CefBrowser.java':'package org.cef.browser;public class CefBrowser{public String url="mod://muxi_terminal/terminal/index.html";public long frameId=1;public boolean missing;public java.util.List<CefFrame> wrappers=new java.util.ArrayList<>();public String getURL(){return url;}public CefFrame getMainFrame(){if(missing)return null;var f=new CefFrame();f.url=url;f.id=frameId;wrappers.add(f);return f;}}',
'net/minecraft/client/Minecraft.java':'package net.minecraft.client;public class Minecraft{public static final Minecraft M=new Minecraft();public Object screen=new net.muxigame.terminal.client.TerminalScreen(),level=new Object(),connection=new Object();public Player player=new Player();public boolean focused=true;public static Minecraft getInstance(){return M;}public boolean isWindowActive(){return focused;}public Object getConnection(){return connection;}public static class Player{public boolean isAlive(){return true;}}}',
'net/muxigame/terminal/client/TerminalScreen.java':'package net.muxigame.terminal.client;public class TerminalScreen{}',
'net/muxigame/terminal/client/TerminalBrowserSession.java':'package net.muxigame.terminal.client;import org.cef.browser.*;public class TerminalBrowserSession{public enum Kind{BUILTIN,WEB}public record State(Kind kind){}public static Kind kind=Kind.BUILTIN;public static CefBrowser app=new CefBrowser(),shell=new CefBrowser();public static long gen=1;public static CefBrowser content(){return app;}public static State state(){return new State(kind);}public static boolean isShell(CefBrowser b){return b==shell;}public static long generation(){return gen;}public static boolean trusted(CefBrowser b,CefFrame f){return (b==app||b==shell)&&f!=null&&f.main&&!f.disposed&&b.url.equals(f.url);}}',
'net/muxigame/terminal/client/map/NativeMapLauncher.java':'package net.muxigame.terminal.client.map;public class NativeMapLauncher{public static java.util.function.BooleanSupplier context;public static void open(java.util.function.BooleanSupplier valid)throws ReflectiveOperationException{context=valid;}}',
'net/muxigame/terminal/client/MapLifecycleTest.java':r'''package net.muxigame.terminal.client;
import org.cef.browser.*;import org.cef.callback.*;import net.minecraft.client.Minecraft;import net.muxigame.terminal.client.map.NativeMapLauncher;
public class MapLifecycleTest{
static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
static class Reply implements CefQueryCallback{String value;int code;public void success(String v){value=v;}public void failure(int c,String m){code=c;}}
static Reply call(CefBrowser b,CefFrame f,String command){var r=new Reply();check(TerminalNativeMapBridge.dispatch(b,f,command,r),"recognized map command");return r;}
public static void main(String[]args){
var mc=Minecraft.getInstance();var b=TerminalBrowserSession.app;var f=b.getMainFrame();var r=call(b,f,"map.open");
check(r.code==0&&r.value.contains("\"nativeMap\":true")&&r.value.contains("\"teleportRequested\":false"),"native map opens without requesting teleport");
f.dispose();check(NativeMapLauncher.context.getAsBoolean(),"disposed callback wrapper still permits return to original terminal");
check(b.wrappers.getLast().disposed,"temporary live frame released after return predicate");
b.frameId=2;check(!NativeMapLauncher.context.getAsBoolean(),"new main document cannot inherit old return context");b.frameId=1;
b.url+="#/settings";check(!NativeMapLauncher.context.getAsBoolean(),"changed route invalidates return");b.url=f.url;
TerminalBrowserSession.gen++;check(!NativeMapLauncher.context.getAsBoolean(),"new terminal epoch invalidates return");TerminalBrowserSession.gen--;
var connection=mc.connection;mc.connection=new Object();check(!NativeMapLauncher.context.getAsBoolean(),"new game connection invalidates return");mc.connection=connection;
b.missing=true;check(!NativeMapLauncher.context.getAsBoolean(),"closed browser has no return context");b.missing=false;
check(call(b,b.getMainFrame(),"map.write").code==404,"unknown map operation denied");
mc.focused=false;check(call(b,b.getMainFrame(),"map.open").code==403,"unfocused terminal denied");mc.focused=true;
TerminalBrowserSession.kind=TerminalBrowserSession.Kind.WEB;check(call(b,b.getMainFrame(),"map.open").code==403,"external content cannot open native map");TerminalBrowserSession.kind=TerminalBrowserSession.Kind.BUILTIN;
var iframe=b.getMainFrame();iframe.main=false;check(call(b,iframe,"map.open").code==403,"iframe denied");
var imposter=new CefBrowser();check(call(imposter,imposter.getMainFrame(),"map.open").code==403,"different browser with same ID denied");
System.out.println("{\"success\":true,\"checks\":"+checks+",\"scope\":\"production map bridge with MC/CEF/launcher fixtures; no native UI\"}");
}}
'''
}
paths=[]
for name,data in files.items():
 p=OUT/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data,encoding='utf-8');paths.append(p)
classes=OUT/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(JDK/'bin/javac.exe'),'--release','21','-encoding','UTF-8','-d',str(classes),str(SOURCE),*map(str,paths)],check=True,capture_output=True)
p=subprocess.run([str(JDK/'bin/java.exe'),'-cp',str(classes),'net.muxigame.terminal.client.MapLifecycleTest'],capture_output=True,text=True,encoding='utf-8',check=True)
result=json.loads(p.stdout);result['source']=str(SOURCE);result['productionSourceSha256']=hashlib.sha256(SOURCE.read_bytes()).hexdigest()
(OUT/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
