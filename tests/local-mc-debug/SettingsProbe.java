package net.muxigame.localdebug;
import com.google.gson.*;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

public final class SettingsProbe {
 public static final java.util.concurrent.atomic.AtomicInteger reloadRequests=new java.util.concurrent.atomic.AtomicInteger(),reloadCompleted=new java.util.concurrent.atomic.AtomicInteger(); static int last=-1,drawTick=-1,ticks;static JsonObject source;static final LinkedHashMap<String,JsonObject> drawn=new LinkedHashMap<>();
 static CompletableFuture<?> reload;static int pendingId;static boolean awaitScreen;static String lastError="";
 static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
 static String formatted(String s){return s.replaceAll("%(\\d+\\$)?[\\d.]*[df]","%$1s");}
 public static void draw(GuiGraphics g,Font font,String text,float x,float y){
  Minecraft mc=Minecraft.getInstance();if(mc.screen==null||text==null||text.isBlank())return;
  if(drawTick!=ticks){drawn.clear();drawTick=ticks;}
  var point=g.pose().last().pose().transformPosition(new org.joml.Vector3f(x,y,0));var end=g.pose().last().pose().transformPosition(new org.joml.Vector3f(x+font.width(text),y+font.lineHeight,0));
  var row=new JsonObject();row.addProperty("text",text);row.addProperty("x",point.x);row.addProperty("y",point.y);row.addProperty("width",end.x-point.x);row.addProperty("height",end.y-point.y);
  try{var f=GuiGraphics.class.getDeclaredField("scissorStack");f.setAccessible(true);var stack=f.get(g);var m=stack.getClass().getDeclaredMethod("peek");m.setAccessible(true);var rect=(net.minecraft.client.gui.navigation.ScreenRectangle)m.invoke(stack);if(rect!=null){var box=new JsonObject();box.addProperty("left",rect.left());box.addProperty("right",rect.right());box.addProperty("top",rect.top());box.addProperty("bottom",rect.bottom());row.add("clip",box);}}
  catch(Exception ignored){}
  drawn.put(text+"@"+point.x+","+point.y,row);if(drawn.size()>700)drawn.remove(drawn.keySet().iterator().next());
 }
 static Object field(Object value,String name)throws Exception{for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static Object invoke(Object value,String method,Class<?>[] types,Object... args)throws Exception{for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(value,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(method);}
 static void cursor(Minecraft mc,double x,double y)throws Exception{double g=mc.getWindow().getGuiScale();invoke(mc.mouseHandler,"onMove",new Class[]{long.class,double.class,double.class},mc.getWindow().getWindow(),x*g,y*g);}
 static void click(Minecraft mc,double x,double y)throws Exception{cursor(mc,x,y);invoke(mc.mouseHandler,"onPress",new Class[]{long.class,int.class,int.class,int.class},mc.getWindow().getWindow(),0,1,0);invoke(mc.mouseHandler,"onPress",new Class[]{long.class,int.class,int.class,int.class},mc.getWindow().getWindow(),0,0,0);}
 static void widgets(Object element,JsonArray output,Set<Object> seen){
  if(element==null||!seen.add(element))return;
  if(element instanceof AbstractWidget w){var row=new JsonObject();row.addProperty("class",element.getClass().getName());row.addProperty("text",w.getMessage().getString());row.addProperty("x",w.getX());row.addProperty("y",w.getY());row.addProperty("width",w.getWidth());row.addProperty("height",w.getHeight());row.addProperty("active",w.active);row.addProperty("visible",w.visible);output.add(row);}
  if(element instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)for(var child:container.children())widgets(child,output,seen);
 }
 static JsonObject snapshot(Minecraft mc)throws Exception{
  var s=new JsonObject();s.addProperty("tick",ticks);s.addProperty("reloadRequests",reloadRequests.get());s.addProperty("reloadCompleted",reloadCompleted.get());s.addProperty("screen",mc.screen==null?"NONE":mc.screen.getClass().getName());s.addProperty("title",mc.screen==null?"":mc.screen.getTitle().getString());s.addProperty("connected",mc.player!=null);s.addProperty("locale",mc.getLanguageManager().getSelected());s.addProperty("overlay",mc.getOverlay()!=null);s.addProperty("guiWidth",mc.getWindow().getGuiScaledWidth());s.addProperty("guiHeight",mc.getWindow().getGuiScaledHeight());s.addProperty("guiScale",mc.getWindow().getGuiScale());s.addProperty("frameWidth",mc.getWindow().getWidth());s.addProperty("frameHeight",mc.getWindow().getHeight());s.addProperty("error",lastError);
  s.addProperty("videoLabel",I18n.get("options.video"));s.addProperty("irisSettingsLabel",I18n.get("muxi_localization.iris.settings"));s.addProperty("irisDownloadLabel",I18n.get("muxi_localization.iris.download_shaders"));var mods=new JsonObject();for(var info:ModList.get().getMods())mods.addProperty(info.getModId(),info.getVersion().toString());s.add("mods",mods);
  var ids=new JsonArray();for(var id:mc.getResourcePackRepository().getSelectedIds())ids.add(id);s.add("visiblePacks",ids);var children=new JsonArray();for(var pack:mc.getResourcePackRepository().getSelectedPacks()){var row=new JsonObject();row.addProperty("id",pack.getId());row.addProperty("hidden",pack.isHidden());children.add(row);}s.add("allPacks",children);
  var rows=new JsonArray();drawn.values().forEach(rows::add);s.add("draw",rows);var list=new JsonArray();widgets(mc.screen,list,Collections.newSetFromMap(new IdentityHashMap<>()));s.add("widgets",list);
  if(mc.screen!=null){var fields=new JsonArray();for(Class<?> cls=mc.screen.getClass();cls!=Screen.class&&cls!=null;cls=cls.getSuperclass())for(var f:cls.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){var row=new JsonObject();row.addProperty("name",f.getName());row.addProperty("type",f.getType().getName());fields.add(row);}s.add("fields",fields);}
  if(source!=null){var checks=new JsonArray();int addedOk=0,existingOk=0,formattedOk=0;for(var ns:source.entrySet()){var record=ns.getValue().getAsJsonObject();for(var entry:record.getAsJsonObject("added").entrySet()){String key=entry.getKey();String expected=mc.getLanguageManager().getSelected().equals("zh_cn")?formatted(entry.getValue().getAsString()):formatted(record.getAsJsonObject("english").get(key).getAsString());if(key.equals("sodium.options.buttons.apply")&&mc.getLanguageManager().getSelected().equals("zh_cn"))expected="\u73a9\u5bb6\u81ea\u9009\u5e94\u7528";String actual=net.minecraft.locale.Language.getInstance().getOrDefault(key);boolean ok=expected.equals(actual);if(ok)addedOk++;var row=new JsonObject();row.addProperty("key",key);row.addProperty("actual",actual);row.addProperty("expected",expected);row.addProperty("passed",ok);checks.add(row);if(expected.contains("%s")||expected.matches(".*%[0-9]+\\$s.*")){String expanded=I18n.get(key,"7","9","11","13");row.addProperty("expanded",expanded);String wanted=String.format(expected,"7","9","11","13");if(expanded.equals(wanted))formattedOk++;else row.addProperty("formatFailed",true);}}
   if(mc.getLanguageManager().getSelected().equals("zh_cn"))for(var entry:record.getAsJsonObject("original").entrySet())if(net.minecraft.locale.Language.getInstance().getOrDefault(entry.getKey()).equals(formatted(entry.getValue().getAsString())))existingOk++;
  }s.addProperty("addedPassed",addedOk);s.addProperty("existingChinesePassed",existingOk);s.addProperty("placeholderPassed",formattedOk);s.add("translations",checks);}
  return s;
 }
 static void result(Minecraft mc,int id,Throwable error){try{var out=DebugFiles.result(id,"host");out.addProperty("ok",error==null);out.addProperty("nativeMinecraftClient",true);out.addProperty("physicalOSInput",false);if(error!=null)out.addProperty("error",error.toString());out.add("status",snapshot(mc));DebugFiles.write("settings-result-"+id+".json",out);}catch(Throwable ex){lastError=ex.toString();}}
 public static void tick(Minecraft mc){ticks++;try{
  if(source==null)source=read(Path.of(System.getProperty("settings.qa.languages")));
  if(reload!=null){if(!reload.isDone())return;Throwable error=null;try{reload.join();}catch(Throwable ex){error=ex;}reload=null;result(mc,pendingId,error);}
  if(awaitScreen){awaitScreen=false;result(mc,pendingId,null);}
  var body=DebugFiles.read("settings-host.json");if(body==null||body.get("id").getAsInt()<=last)return;last=body.get("id").getAsInt();String action=body.get("action").getAsString();pendingId=last;
  switch(action){
   case "observe":break;
   case "world":mc.setScreen(null);drawn.clear();awaitScreen=true;return;
   case "video":for(var widget:mc.screen.children())if(widget instanceof Button b && b.getMessage().getString().equals(I18n.get("options.video"))){b.onPress();drawn.clear();awaitScreen=true;return;}throw new IllegalStateException("Video settings widget not found");
   case "options":mc.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(mc.screen,mc.options));drawn.clear();awaitScreen=true;return;
   case "shader":mc.setScreen((Screen)Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen").getConstructor(Screen.class).newInstance(mc.screen));drawn.clear();awaitScreen=true;return;
   case "scale":mc.options.guiScale().set(body.get("value").getAsInt());mc.resizeDisplay();drawn.clear();awaitScreen=true;return;
   case "close":if(mc.screen!=null)mc.screen.onClose();drawn.clear();awaitScreen=true;return;
   case "click":click(mc,body.get("x").getAsDouble(),body.get("y").getAsDouble());drawn.clear();awaitScreen=true;return;
   case "cursor":cursor(mc,body.get("x").getAsDouble(),body.get("y").getAsDouble());break;
   case "search":var search=field(mc.screen,"searchTextField");invoke(search,"setText",new Class[]{String.class},body.get("text").getAsString());drawn.clear();awaitScreen=true;return;
   case "locale":String lang=body.get("value").getAsString();mc.getLanguageManager().setSelected(lang);mc.options.languageCode=lang;reload=mc.reloadResourcePacks();return;
   case "reload":reload=mc.reloadResourcePacks();return;
   case "screenshot":Screenshot.grab(mc.gameDirectory,body.get("filename").getAsString(),mc.getMainRenderTarget(),msg->{});break;
   default:throw new IllegalArgumentException(action);
  }result(mc,last,null);
 }catch(Throwable ex){lastError=ex.toString();result(mc,last,ex);}}
}
