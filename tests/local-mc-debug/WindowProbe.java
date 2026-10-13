package net.muxigame.localdebug;
import com.google.gson.*;
import com.cinemamod.mcef.*;
import net.minecraft.client.Minecraft;
import net.muxigame.terminal.client.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;import java.util.*;
@Mod(value="local_mc_debug_qa",dist=Dist.CLIENT)
public final class WindowProbe {
 int last; static volatile String source="";static volatile JsonObject dom;static volatile int domBrowser;
 public WindowProbe(){if(DebugFiles.enabled())NeoForge.EVENT_BUS.addListener(this::tick);}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static JsonObject rect(Object o)throws Exception{JsonObject r=new JsonObject();for(String n:new String[]{"x","y","width","height"}){var m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);r.addProperty(n,((Number)m.invoke(o)).intValue());}return r;}
 static JsonObject status()throws Exception{
  var mc=Minecraft.getInstance();var out=new JsonObject();out.addProperty("mcef",MCEF.isInitialized());out.addProperty("screen",mc.screen==null?"NONE":mc.screen.getClass().getSimpleName());
  out.addProperty("guiWidth",mc.getWindow().getGuiScaledWidth());out.addProperty("guiHeight",mc.getWindow().getGuiScaledHeight());out.addProperty("guiScale",mc.getWindow().getGuiScale());out.addProperty("framebufferWidth",mc.getWindow().getWidth());out.addProperty("framebufferHeight",mc.getWindow().getHeight());
  out.addProperty("reloading",mc.getOverlay()!=null);
  if(mc.screen instanceof TerminalScreen s){out.addProperty("fullscreen",(Boolean)field(s,"fullscreen"));Object l=field(s,"windowLayout");var obj=new JsonObject();for(String n:new String[]{"outer","content","close","windowMode"}){var m=l.getClass().getDeclaredMethod(n);m.setAccessible(true);obj.add(n,rect(m.invoke(l)));}out.add("layout",obj);}
  var shell=TerminalBrowserSession.current();var app=TerminalBrowserSession.content();var b=TerminalBrowserSession.activeBrowser();
  if(shell!=null){out.addProperty("shellId",shell.getIdentifier());out.addProperty("shellTexture",shell.getRenderer().getTextureID());}
  if(app!=null){out.addProperty("appId",app.getIdentifier());out.addProperty("appTexture",app.getRenderer().getTextureID());}
  if(b!=null){out.addProperty("activeId",b.getIdentifier());out.addProperty("url",b.getURL());}
  out.addProperty("appVisible",TerminalBrowserSession.contentVisible());
  out.add("state",new Gson().toJsonTree(TerminalBrowserSession.state()));
  if(dom!=null&&b!=null&&b.getIdentifier()==domBrowser)out.add("dom",dom.deepCopy());
  return out;
 }
 static void readDom(){
  var b=TerminalBrowserSession.activeBrowser();if(b==null)return;int ident=b.getIdentifier();
  String js="(()=>{const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}};const es=[...document.querySelectorAll('button,[data-open],[data-action],input')].filter(e=>e.getBoundingClientRect().width&&e.getBoundingClientRect().height).map(e=>({open:e.dataset.open||'',action:e.dataset.action||'',text:(e.innerText||e.getAttribute('aria-label')||'').slice(0,80),...rect(e)}));document.documentElement.dataset.windowUiQa=btoa(unescape(encodeURIComponent(JSON.stringify({width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight,hash:location.hash,controls:es}))));})()";
  b.executeJavaScript(js,b.getURL(),0);
  b.getSource(s->{try{String key="data-window-ui-qa=\"";int at=s.indexOf(key);if(at<0)return;int start=at+key.length(),end=s.indexOf('"',start);String decoded=new String(Base64.getDecoder().decode(s.substring(start,end)),java.nio.charset.StandardCharsets.UTF_8);if(TerminalBrowserSession.activeBrowser()!=b)return;dom=JsonParser.parseString(decoded).getAsJsonObject();domBrowser=ident;}catch(Exception ignored){}});
 }
 void tick(ClientTickEvent.Post e){try{var c=DebugFiles.read("window-host.json");if(c==null||c.get("id").getAsInt()<=last)return;last=c.get("id").getAsInt();var out=DebugFiles.result(last,"host");try{var mc=Minecraft.getInstance();switch(c.get("action").getAsString()){
  case "observe"->{}
  case "script"->{var b=TerminalBrowserSession.activeBrowser();b.executeJavaScript(Files.readString(Path.of(c.get("path").getAsString())),b.getURL(),0);}
  case "eval"->{var b=TerminalBrowserSession.activeBrowser();int ident=b.getIdentifier();String js="(()=>{try{document.documentElement.dataset.windowUiQa=btoa(unescape(encodeURIComponent(JSON.stringify(("+c.get("expression").getAsString()+")))));}catch(e){document.documentElement.dataset.windowUiQa=btoa(JSON.stringify({qaError:String(e)}));}})()";b.executeJavaScript(js,b.getURL(),0);b.getSource(v->{try{String key="data-window-ui-qa=\"";int at=v.indexOf(key);if(at<0)return;int begin=at+key.length(),end=v.indexOf('\"',begin);dom=JsonParser.parseString(new String(Base64.getDecoder().decode(v.substring(begin,end)),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();domBrowser=ident;}catch(Exception ignored){}});}

  case "open"->{TerminalClient.openTerminal();}
  case "scale"->{mc.options.guiScale().set(c.get("value").getAsInt());mc.resizeDisplay();}
  case "dom"->{readDom();}
  case "app"->{TerminalBrowserSession.openApp(c.get("name").getAsString());}
  case "cursor"->{var m=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);m.setAccessible(true);double scale=mc.getWindow().getGuiScale();m.invoke(mc.mouseHandler,mc.getWindow().getWindow(),c.get("x").getAsDouble()*scale,c.get("y").getAsDouble()*scale);}
  case "mouse"->{var m=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress",long.class,int.class,int.class,int.class);m.setAccessible(true);m.invoke(mc.mouseHandler,mc.getWindow().getWindow(),c.get("button").getAsInt(),c.get("pressed").getAsBoolean()?1:0,0);}
  case "keyboard"->{mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),c.get("key").getAsInt(),0,c.get("pressed").getAsBoolean()?1:0,0);}
  case "screenshot"->{net.minecraft.client.Screenshot.grab(mc.gameDirectory,c.get("filename").getAsString(),mc.getMainRenderTarget(),x->{});}
  default->throw new IllegalArgumentException("Unknown bounded action");
 }out.addProperty("ok",true);out.addProperty("physicalOSInput",false);out.add("status",status());
 }catch(Throwable failure){failure.printStackTrace();out.addProperty("ok",false);out.addProperty("error",failure.toString());}DebugFiles.write("window-result-"+last+".json",out);}catch(Exception failure){failure.printStackTrace();}}
}