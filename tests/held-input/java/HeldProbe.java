package net.muxigame.terminal.heldqa;
import com.google.gson.*;
import com.cinemamod.mcef.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.muxigame.terminal.client.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.GL11;
import java.util.*;
import java.security.MessageDigest;
@Mod(value="terminal_held_qa",dist=Dist.CLIENT)
public final class HeldProbe {
 public static final JsonArray keys=new JsonArray();public static final Set<Integer> textures=new HashSet<>();
 public static int mainDraws,offDraws,twoArmDraws,oneArmDraws,paints;public static volatile JsonObject dom;
 int last=-1,ticks,finish;JsonObject pending;boolean query;com.mojang.blaze3d.platform.InputConstants.Key savedRight;net.neoforged.neoforge.client.settings.KeyModifier savedModifier;
 public HeldProbe(){if(HeldFiles.enabled())NeoForge.EVENT_BUS.addListener(this::tick);}
 public static void key(Object b,int key,int action){if(!HeldFiles.enabled())return;var row=new JsonObject();row.addProperty("browser",System.identityHashCode(b));row.addProperty("key",key);row.addProperty("action",action);keys.add(row);}
 public static void dom(String value){dom=JsonParser.parseString(value).getAsJsonObject();}
 static Screen other(){return new Screen(Component.literal("Held QA other Screen")){};}
 static Object field(Class<?> c,Object target,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static JsonObject sample()throws Exception{
  var mc=Minecraft.getInstance();var row=new JsonObject();row.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());row.addProperty("windowActive",mc.isWindowActive());row.addProperty("connected",mc.getConnection()!=null);row.addProperty("mainDraws",mainDraws);row.addProperty("offDraws",offDraws);row.addProperty("twoArmDraws",twoArmDraws);row.addProperty("oneArmDraws",oneArmDraws);row.addProperty("paints",paints);row.addProperty("shell",TerminalBrowserSession.current()==null?0:System.identityHashCode(TerminalBrowserSession.current()));row.addProperty("content",TerminalBrowserSession.content()==null?0:System.identityHashCode(TerminalBrowserSession.content()));row.addProperty("kind",TerminalBrowserSession.state().kind().name());row.addProperty("contentVisible",TerminalBrowserSession.contentVisible());row.addProperty("viewId",TerminalBrowserSession.state().viewId());row.add("keys",keys.deepCopy());
  int live=0;for(int id:textures)if(GL11.glIsTexture(id))live++;row.addProperty("liveTextures",live);
  var app=MCEF.getApp().getHandle();var clients=(Collection<?>)field(org.cef.CefApp.class,app,"clients_");synchronized(app){row.addProperty("cefClients",clients.size());}
  var capture=field(TerminalHeldInput.class,null,"CAPTURE");row.addProperty("heldPressed",((Collection<?>)field(TerminalHeldInput.class,null,"PRESSED")).size());row.addProperty("heldTarget",field(capture.getClass(),capture,"target")==null?0:System.identityHashCode(field(capture.getClass(),capture,"target")));
  if(mc.level!=null)row.addProperty("dimension",mc.level.dimension().location().toString());row.addProperty("playerAlive",mc.player!=null&&mc.player.isAlive());row.addProperty("rightGameDown",mc.options.keyRight.isDown());row.addProperty("rightGameClicks",((Number)field(net.minecraft.client.KeyMapping.class,mc.options.keyRight,"clickCount")).intValue());row.addProperty("coreFrameCleared",field(Class.forName("net.muxigame.core.client.input.GameplayInputPriority"),null,"frame")==null);
  if(TerminalBrowserSession.current()!=null){int id=TerminalBrowserSession.current().getRenderer().getTextureID();int before=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);try{int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);row.addProperty("textureWidth",w);row.addProperty("textureHeight",h);if(w>0&&h>0){var pixels=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] bytes=new byte[pixels.remaining()];pixels.get(bytes);row.addProperty("textureHash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,before);}}
  return row;
 }
 void ask(MCEFBrowser b,int id,String code){dom=null;b.executeJavaScript(code+";window.muxiTerminalQuery({request:'heldqa:'+JSON.stringify({id:"+id+",hash:location.hash,page:document.querySelector('.page-active')?.id,selected:navItems().indexOf(document.activeElement),label:document.activeElement?.textContent,events:window.__heldEvents||[],pulse:window.__heldPulse||0}),onSuccess:()=>{},onFailure:(c,m)=>console.error(c,m)});",TerminalBrowserSession.HOME_URL,0);}
 void tick(ClientTickEvent.Post event){
  ticks++;var mc=Minecraft.getInstance();
  try{
   if(pending!=null&&ticks>=finish){if(query&&(dom==null||dom.get("id").getAsInt()!=last))return;pending.add("sample",sample());if(query)pending.add("dom",dom);HeldFiles.write("held-result-"+last+".json",pending);pending=null;}
   if(pending!=null)return;var cmd=HeldFiles.read("held-command.json");if(cmd==null||cmd.get("id").getAsInt()<=last)return;last=cmd.get("id").getAsInt();pending=new JsonObject();pending.addProperty("id",last);pending.addProperty("ok",true);pending.addProperty("nativeCallback",true);query=false;
   switch(cmd.get("type").getAsString()){
    case "sample"->{}
    case "focus"->{mc.setWindowActive(cmd.get("active").getAsBoolean());pending.addProperty("assistedNativeFocus",true);}
    case "script"->{MCEFBrowser b=TerminalBrowserSession.activeBrowser();ask(b,last,cmd.get("code").getAsString());query=true;pending.addProperty("assistedDOMFixture",true);}
    case "key"->{int key=cmd.get("key").getAsInt(),mods=cmd.has("modifiers")?cmd.get("modifiers").getAsInt():0;long window=mc.getWindow().getWindow();int action=cmd.has("action")?cmd.get("action").getAsInt():1;mc.keyboardHandler.keyPress(window,key,0,action,mods);if(!cmd.has("action"))mc.keyboardHandler.keyPress(window,key,0,0,mods);}
    case "screen"->{switch(cmd.get("value").getAsString()){case "none"->mc.setScreen(null);case "terminal"->TerminalClient.openTerminal();case "chat"->mc.setScreen(new ChatScreen(""));default->mc.setScreen(other());}}
    case "arrow-binding"->{if(cmd.get("active").getAsBoolean()){savedRight=mc.options.keyRight.getKey();savedModifier=mc.options.keyRight.getKeyModifier();mc.options.keyRight.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(262));}else mc.options.keyRight.setKeyModifierAndCode(savedModifier,savedRight);net.minecraft.client.KeyMapping.resetMapping();pending.addProperty("assistedBindingFixture",true);}
    case "home"->TerminalBrowserSession.home();
    case "screenshot"->mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),291,0,1,0);
    case "logout"->{mc.disconnect();mc.setScreen(other());}
    default->throw new IllegalArgumentException("Unknown owned held QA command");
   }
   finish=ticks+12;
  }catch(Exception failed){if(pending==null)pending=new JsonObject();pending.addProperty("id",last);pending.addProperty("ok",false);pending.addProperty("error",failed.toString());try{HeldFiles.write("held-result-"+last+".json",pending);}catch(Exception busy){}pending=null;}
 }
}
