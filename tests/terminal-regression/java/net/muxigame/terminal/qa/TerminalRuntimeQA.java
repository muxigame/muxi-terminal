package net.muxigame.terminal.qa;

import com.cinemamod.mcef.MCEF;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.muxigame.terminal.MuxiTerminal;
import net.muxigame.terminal.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.*;
import org.cef.network.CefRequest;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

@Mod(value="terminal_qa",dist=Dist.CLIENT)
public final class TerminalRuntimeQA {
 private int ticks,frames;
 private final PhoenixVisibleQA phoenix=new PhoenixVisibleQA();
 private volatile int stage;
 private boolean starting,ready,finished,requested;
 private volatile JsonObject dom;
 private long domGeneration;
 private int domSerial;
 private Runnable domRead;
 private int domReadAt;
 private boolean heldRevealStarted;
 private static boolean heldOpenShot,heldCloseShot;
 private static final Set<String> heldMotionShots=new HashSet<>();
 private static final JsonArray heldMotionSamples=new JsonArray();
 private final JsonObject report=new JsonObject();
 private static final JsonObject probes=new JsonObject();
 private static final JsonArray clockChanges=new JsonArray();
 private static Matrix4f pose;
 private static Matrix3f normal;
 private static int depth;
 private static JsonObject state;
 private static boolean inTerminal;
 private static int probeStage;
 private static final String[] names={"empty","block","main-right-two","main-right-other","off-left-other","off-left-two","main-left-two","main-left-other","off-right-other","off-right-two","dual-terminal","swing","ui-home","guide","tasks","reopen-home","reopen-tasks","repeat-guide","repeat-tasks","closed-held","shader-night","vanilla-night","vanilla-day","graphics-options","after-empty","after-block","held-guide-day","held-tasks-day","held-opening","held-closing","held-tasks-night","held-tasks-night-off","after-content-block","held-night-opening","held-night-closing","held-night-off-opening","held-night-off-closing","after-night-content-block","held-day-off-opening","held-day-off-closing","after-all-content-block"};
 public TerminalRuntimeQA(){if(System.getProperty("qa.mode","").equals("guns")){new GunDiagnosticRuntime();return;}NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::renderFrame);}
 public static void before(PoseStack stack){inTerminal=true;String key=names[Math.min(probeStage,names.length-1)];probes.add(key,new JsonObject());pose=new Matrix4f(stack.last().pose());normal=new Matrix3f(stack.last().normal());depth=depth(stack);state=gl();}
 public static void after(PoseStack stack){
  inTerminal=false;String key=names[Math.min(probeStage,names.length-1)];JsonObject p=probes.has(key)?probes.getAsJsonObject(key):new JsonObject();
  p.addProperty("poseBalanced",pose.equals(stack.last().pose())&&normal.equals(stack.last().normal())&&depth==depth(stack));
  p.add("glBefore",state);p.add("glAfter",gl());probes.add(key,p);
 }
 private static int depth(PoseStack s){try{for(var f:PoseStack.class.getDeclaredFields())if(Deque.class.isAssignableFrom(f.getType())){f.setAccessible(true);return ((Deque<?>)f.get(s)).size();}}catch(Exception e){}return -1;}
 private static JsonObject gl(){JsonObject p=new JsonObject();p.addProperty("blend",GL11.glIsEnabled(GL11.GL_BLEND));p.addProperty("depth",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));p.addProperty("depthMask",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));p.addProperty("shader",String.valueOf(RenderSystem.getShader()));p.addProperty("texture0",RenderSystem.getShaderTexture(0));return p;}
 public static void arm(PoseStack stack,ModelPart arm,int light){
  if(!inTerminal)return;
  Minecraft mc=Minecraft.getInstance();var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
  if(arm!=renderer.getModel().rightArm&&arm!=renderer.getModel().leftArm)return;
  String key=names[Math.min(probeStage,names.length-1)];JsonObject p=probes.has(key)?probes.getAsJsonObject(key):new JsonObject();
  Matrix4f m=new Matrix4f(stack.last().pose());PoseStack local=new PoseStack();arm.translateAndRotate(local);m.mul(local.last().pose());
  Vector3f shoulder=m.transformPosition(new Vector3f(0,0,0));Vector3f palm=m.transformPosition(new Vector3f(0,12f/16f,0));
  JsonObject a=new JsonObject();a.addProperty("shoulderY",shoulder.y);a.addProperty("palmY",palm.y);a.addProperty("extendsFromBelow",shoulder.y<palm.y);a.addProperty("xRot",arm.xRot);a.addProperty("yRot",arm.yRot);a.addProperty("zRot",arm.zRot);a.addProperty("packedLight",light);
  p.add(arm.x>0?"leftArm":"rightArm",a);probes.add(key,p);
 }
 private void renderFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event){
  if(finished || !Set.of(28,29,33,34,35,36,38,39).contains(stage))return;
  try{
   var motion=TerminalBrowserSession.contentMotion();
   if(!motion.animating() || motion.alpha()<=0.02f || motion.alpha()>=0.98f)return;
   JsonObject sample=new JsonObject();sample.addProperty("stage",stage);sample.addProperty("alpha",motion.alpha());sample.addProperty("scale",motion.scale());sample.addProperty("closing",motion.closing());sample.addProperty("generation",TerminalBrowserSession.generation());heldMotionSamples.add(sample);
   if(motion.alpha()>0.15f && motion.alpha()<0.85f){
    String tag=names[stage]+"-mid.png";if(heldMotionShots.add(tag))capture(tag);
    if(stage==28)heldOpenShot=true;if(stage==29)heldCloseShot=true;
   }
  }catch(Exception e){throw new RuntimeException(e);}
 }
 private static boolean contentReady(){
  return TerminalBrowserSession.content()!=null && TerminalBrowserSession.contentVisible() && TerminalBrowserSession.state().rendered() && !TerminalBrowserSession.state().loading() && !TerminalBrowserSession.contentMotion().animating();
 }
 private boolean domReady(int n){
  if(dom==null || dom.get("stage").getAsInt()!=n || dom.get("generation").getAsLong()!=domGeneration)return false;
  if(!"function".equals(dom.get("terminalBinding").getAsString()))return false;
  if(n==13 || n==17 || n==26){
   var icons=dom.getAsJsonArray("icons");if(icons.size()!=10)return false;
   for(var item:icons)if(item.getAsJsonObject().get("width").getAsInt()<=0 || !item.getAsJsonObject().get("displayed").getAsBoolean())return false;
   return dom.get("url").getAsString().endsWith("#/guide");
  }
  return dom.get("url").getAsString().endsWith("#/tasks") && dom.get("tasks").getAsInt()>0;
 }
 private void tick(ClientTickEvent.Post event){
  if(finished)return;Minecraft mc=Minecraft.getInstance();
  try{
   if(++ticks>18000)throw new IllegalStateException("Timeout stage "+stage);
   if(domRead!=null && ticks>=domReadAt){Runnable pending=domRead;domRead=null;pending.run();}
   if(!ready){if(!MCEF.isInitialized())return;ready=true;}
   if(!starting){
    if(mc.getOverlay()!=null||mc.screen==null)return;starting=true;mc.options.renderDistance().set(3);mc.options.bobView().set(false);mc.options.graphicsMode().set(GraphicsStatus.FAST);mc.options.setCameraType(CameraType.FIRST_PERSON);
    if(mc.getLevelSource().levelExists("terminal-fullpack-qa")){mc.createWorldOpenFlows().openWorld("terminal-fullpack-qa",()->{});return;}
    mc.createWorldOpenFlows().createFreshLevel("terminal-fullpack-qa",new LevelSettings("Isolated terminal full pack QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(987654321L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
   }
   if(ticks%100==0)Files.writeString(Path.of("runtime-progress.json"),"{\"stage\":"+stage+",\"screen\":"+new Gson().toJson(mc.screen==null?"":mc.screen.getClass().getName())+",\"frames\":"+frames+"}");
   if(stage==41){if(++frames<120)return;capture("logout-title.png");report.addProperty("logoutScreen",mc.screen==null?"":mc.screen.getClass().getName());report.addProperty("completed",true);report.addProperty("heldOpeningCaptured",heldOpenShot);report.addProperty("heldClosingCaptured",heldCloseShot);report.add("heldMotionSamples",heldMotionSamples);report.add("heldMotionShots",new Gson().toJsonTree(heldMotionShots));Files.writeString(Path.of("runtime-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));finished=true;mc.stop();return;}
   if(mc.player==null&&ticks%40==0&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen)){
    for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.active){b.onPress();break;}
   }
   if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
   if(stage==0&&frames==0){mc.setScreen(null);mc.player.setYRot(180);mc.player.setXRot(8);hands(HumanoidArm.RIGHT,ItemStack.EMPTY,ItemStack.EMPTY);time(6000);report.addProperty("graphicsMode",mc.options.graphicsMode().get().toString());report.addProperty("shader",shader());report.addProperty("modCount",ModList.get().size());report.addProperty("gpuRenderer",GL11.glGetString(GL11.GL_RENDERER));report.addProperty("windowVisible",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==org.lwjgl.glfw.GLFW.GLFW_TRUE);JsonObject versions=new JsonObject();for(var mod:ModList.get().getMods())if(Set.of("minecraft","neoforge","create","flywheel","sodium","iris","colorwheel","vanillin","firstperson","notenoughanimations","skinlayers3d","sway","yes_steve_model","mcef","webdisplays","muxi_game_core","muxi_terminal").contains(mod.getModId()))versions.addProperty(mod.getModId(),mod.getVersion().toString());report.add("renderModVersions",versions);}
   if(stage==40&&!phoenix.tick())return;
   probeStage=stage;
   ++frames;
   if((stage==28||stage==33||stage==35||stage==38) && !heldRevealStarted){
    if(!TerminalBrowserSession.state().rendered() || TerminalBrowserSession.state().loading()){if(frames>600)throw new IllegalStateException("Held child not rendered");return;}
    domGeneration=TerminalBrowserSession.generation();if(frames%20==0)requestDom(stage);if(!domReady(stage)){if(frames>600)throw new IllegalStateException("Hidden held DOM not ready");return;}
    var view=TerminalBrowserSession.state();
    if(!TerminalBrowserSession.revealView(view.viewId(),view.launchToken(),true,false))throw new IllegalStateException("Held reveal rejected");
    heldRevealStarted=true;frames=0;return;
   }
   if(stage==11&&frames==3){mc.player.swing(InteractionHand.MAIN_HAND);}
   if(stage==11&&frames==5)capture("swing-active.png");
   if(stage>=2&&stage<=9&&frames==3)capture(names[stage]+"-equip.png");
   if(frames<100)return;
   if(stage==13||stage==14||stage==16||stage==17||stage==18||stage==26||stage==27||stage==30||stage==31){
    if(!requested){
     if(!contentReady()){if(frames>600)throw new IllegalStateException("Content not ready "+TerminalBrowserSession.state());return;}
     capture(names[stage]+"-before-sample.png");requested=true;dom=null;domGeneration=TerminalBrowserSession.generation();requestDom(stage);
    }
    if(!domReady(stage)){
     if(frames%20==0)requestDom(stage);
     if(frames>600){if(dom!=null)report.add("lastDom",dom.deepCopy());throw new IllegalStateException("DOM sampler timeout "+stage+" state="+TerminalBrowserSession.state());}
     return;
    }
    report.add(names[stage],dom.deepCopy());
   }
   capture(names[stage]+".png");
   JsonObject scene=new JsonObject();scene.addProperty("dimension",mc.player.level().dimension().location().toString());scene.addProperty("dayTime",mc.level.getDayTime());scene.addProperty("shader",shader());scene.addProperty("graphics",mc.options.graphicsMode().get().toString());scene.addProperty("mainArm",mc.player.getMainArm().toString());scene.addProperty("mainItem",mc.player.getMainHandItem().getItem().toString());scene.addProperty("offItem",mc.player.getOffhandItem().getItem().toString());scene.addProperty("firstpersonLoaded",ModList.get().isLoaded("firstperson"));scene.addProperty("firstpersonEnabled",firstpersonEnabled());report.add("scene-"+names[stage],scene);
   if(stage==14||stage==18||stage==27||stage==30||stage==31){Class<?> api=Class.forName("net.muxigame.core.client.tasks.TerminalTasksApi");var snapshot=JsonParser.parseString((String)api.getMethod("snapshotJson").invoke(null)).getAsJsonObject();report.add("coreSnapshot"+stage,snapshot);
    if(!snapshot.has("rows") || snapshot.getAsJsonArray("rows").size()!=dom.get("tasks").getAsInt())throw new IllegalStateException("Actual Core rows differ from DOM");}
   switch(stage){
    case 0->hands(HumanoidArm.RIGHT,new ItemStack(Items.STONE),ItemStack.EMPTY);
    case 1->hands(HumanoidArm.RIGHT,terminal(),ItemStack.EMPTY);
    case 2->hands(HumanoidArm.RIGHT,terminal(),new ItemStack(Items.IRON_SWORD));
    case 3->hands(HumanoidArm.RIGHT,new ItemStack(Items.IRON_SWORD),terminal());
    case 4->hands(HumanoidArm.RIGHT,ItemStack.EMPTY,terminal());
    case 5->hands(HumanoidArm.LEFT,terminal(),ItemStack.EMPTY);
    case 6->hands(HumanoidArm.LEFT,terminal(),new ItemStack(Items.IRON_SWORD));
    case 7->hands(HumanoidArm.LEFT,new ItemStack(Items.IRON_SWORD),terminal());
    case 8->hands(HumanoidArm.LEFT,ItemStack.EMPTY,terminal());
    case 9->hands(HumanoidArm.RIGHT,terminal(),terminal());
    case 10->{hands(HumanoidArm.RIGHT,terminal(),ItemStack.EMPTY);mc.player.swing(InteractionHand.MAIN_HAND);}
    case 11->TerminalClient.openHome();
    case 12->TerminalClient.openApp("guide");
    case 13->TerminalClient.openApp("tasks");
    case 14->{mc.screen.onClose();TerminalClient.openHome();}
    case 15->TerminalClient.openApp("tasks");
    case 16->TerminalClient.openApp("guide");
    case 17->TerminalClient.openApp("tasks");
    case 18->mc.screen.onClose();
    case 19->time(18000);
    case 20->{var iris=Class.forName("net.irisshaders.iris.Iris");Object config=iris.getMethod("getIrisConfig").invoke(null);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,false);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);report.addProperty("shaderOff",shader());}
    case 21->time(6000);
    case 22->mc.setScreen(new VideoSettingsScreen(new OptionsScreen(null,mc.options),mc,mc.options));
    case 23->{mc.setScreen(null);hands(HumanoidArm.RIGHT,ItemStack.EMPTY,ItemStack.EMPTY);}
    case 24->hands(HumanoidArm.RIGHT,new ItemStack(Items.STONE),ItemStack.EMPTY);
    case 25->{hands(HumanoidArm.RIGHT,terminal(),ItemStack.EMPTY);time(6000);shaders(true);TerminalBrowserSession.openApp("guide");}
    case 26->TerminalBrowserSession.openApp("tasks");
    case 27->{TerminalBrowserSession.beginLaunch("qa-held-motion",TerminalBrowserSession.Kind.BUILTIN,"QA tasks");TerminalBrowserSession.openApp("tasks");}
    case 28->{if(!heldOpenShot)throw new IllegalStateException("No actual held opening intermediate frame");TerminalBrowserSession.animateHome(false);}
    case 29->{if(!heldCloseShot || TerminalBrowserSession.content()!=null)throw new IllegalStateException("Held close incomplete");time(18000);TerminalBrowserSession.openApp("tasks");}
    case 30->shaders(false);
    case 31->{TerminalBrowserSession.home();hands(HumanoidArm.RIGHT,new ItemStack(Items.STONE),ItemStack.EMPTY);}
    case 32->{shaders(true);time(18000);hands(HumanoidArm.RIGHT,terminal(),ItemStack.EMPTY);heldRevealStarted=false;TerminalBrowserSession.beginLaunch("qa-held-night",TerminalBrowserSession.Kind.BUILTIN,"QA tasks");TerminalBrowserSession.openApp("tasks");}
    case 33->{if(!heldMotionShots.contains("held-night-opening-mid.png"))throw new IllegalStateException("No night opening intermediate frame");TerminalBrowserSession.animateHome(false);}
    case 34->{if(!heldMotionShots.contains("held-night-closing-mid.png") || TerminalBrowserSession.content()!=null)throw new IllegalStateException("Night closing incomplete");shaders(false);heldRevealStarted=false;TerminalBrowserSession.beginLaunch("qa-held-night-off",TerminalBrowserSession.Kind.BUILTIN,"QA tasks");TerminalBrowserSession.openApp("tasks");}
    case 35->{if(!heldMotionShots.contains("held-night-off-opening-mid.png"))throw new IllegalStateException("No night shaders-off opening intermediate frame");TerminalBrowserSession.animateHome(false);}
    case 36->{if(!heldMotionShots.contains("held-night-off-closing-mid.png") || TerminalBrowserSession.content()!=null)throw new IllegalStateException("Night shaders-off closing incomplete");hands(HumanoidArm.RIGHT,new ItemStack(Items.STONE),ItemStack.EMPTY);}
    case 37->{time(6000);hands(HumanoidArm.RIGHT,terminal(),ItemStack.EMPTY);heldRevealStarted=false;TerminalBrowserSession.beginLaunch("qa-held-day-off",TerminalBrowserSession.Kind.BUILTIN,"QA tasks");TerminalBrowserSession.openApp("tasks");}
    case 38->{if(!heldMotionShots.contains("held-day-off-opening-mid.png"))throw new IllegalStateException("No day shaders-off opening intermediate frame");TerminalBrowserSession.animateHome(false);}
    case 39->{if(!heldMotionShots.contains("held-day-off-closing-mid.png") || TerminalBrowserSession.content()!=null)throw new IllegalStateException("Day shaders-off closing incomplete");hands(HumanoidArm.RIGHT,new ItemStack(Items.STONE),ItemStack.EMPTY);}
    case 40->{
     for(int i=2;i<=9;i++){var probe=probes.getAsJsonObject(names[i]);if(probe==null || !probe.has("poseBalanced") || !probe.get("poseBalanced").getAsBoolean())throw new IllegalStateException("Pose stack unbalanced "+names[i]);int arms=0;for(String side:List.of("leftArm","rightArm")){if(probe.has(side)){arms++;if(!probe.getAsJsonObject(side).get("extendsFromBelow").getAsBoolean())throw new IllegalStateException("Arm support direction "+names[i]+" "+side);}}if(arms==0)throw new IllegalStateException("No actual arm probe "+names[i]);}
     for(String sceneName:List.of("shader-night","vanilla-night","held-tasks-night","held-tasks-night-off","held-night-opening","held-night-closing","held-night-off-opening","held-night-off-closing"))if(report.getAsJsonObject("scene-"+sceneName).get("dayTime").getAsLong()%24000<12000)throw new IllegalStateException("Night not actually reached "+sceneName);
     for(String sceneName:List.of("held-guide-day","held-day-off-opening","held-day-off-closing"))if(report.getAsJsonObject("scene-"+sceneName).get("dayTime").getAsLong()%24000>=12000)throw new IllegalStateException("Day not actually reached "+sceneName);
     report.add("probes",probes);report.add("clockChanges",clockChanges);report.addProperty("mode",System.getProperty("qa.mode"));report.addProperty("fixtureTasks",false);report.addProperty("fixtureIcons",false);report.addProperty("samplerAddsQueryRouter",false);report.addProperty("samplerHydratesImages",false);report.addProperty("heldOpeningCaptured",heldOpenShot);report.addProperty("heldClosingCaptured",heldCloseShot);report.add("heldMotionSamples",heldMotionSamples);report.add("heldMotionShots",new Gson().toJsonTree(heldMotionShots));Files.writeString(Path.of("runtime-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));TerminalBrowserSession.close();mc.level.disconnect();mc.disconnect(new TitleScreen());
    }
   }
   stage++;frames=0;requested=false;dom=null;
  }catch(Throwable e){e.printStackTrace();try{report.addProperty("error",e.toString());report.addProperty("stage",stage);report.add("probes",probes);Files.writeString(Path.of("runtime-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception ignored){}finished=true;mc.stop();}
 }
 private static ItemStack terminal(){return new ItemStack(MuxiTerminal.PLAYER_TERMINAL.get());}
 private static void hands(HumanoidArm arm,ItemStack main,ItemStack off){Minecraft mc=Minecraft.getInstance();mc.options.mainHand().set(arm);mc.player.setItemInHand(InteractionHand.MAIN_HAND,main);mc.player.setItemInHand(InteractionHand.OFF_HAND,off);}
 private static void time(long value){Minecraft mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server!=null)server.execute(()->{for(var level:server.getAllLevels()){JsonObject p=new JsonObject();p.addProperty("dimension",level.dimension().location().toString());p.addProperty("dataClass",level.getLevelData().getClass().getName());p.addProperty("requested",value);p.addProperty("before",level.getDayTime());level.setDayTime(value);level.setWeatherParameters(0,6000,false,false);p.addProperty("after",level.getDayTime());clockChanges.add(p);}});}
 private static String shader(){try{var iris=Class.forName("net.irisshaders.iris.Iris");Object config=iris.getMethod("getIrisConfig").invoke(null);return config.getClass().getMethod("getShaderPackName").invoke(config)+" enabled="+config.getClass().getMethod("areShadersEnabled").invoke(config);}catch(Exception e){return e.toString();}}
 private static boolean firstpersonEnabled(){try{return (Boolean)Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI").getMethod("isEnabled").invoke(null);}catch(Exception e){return false;}}
 private static void capture(String file)throws Exception{try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Path.of(file));}}
 private static void shaders(boolean enabled)throws Exception{
  var iris=Class.forName("net.irisshaders.iris.Iris");Object config=iris.getMethod("getIrisConfig").invoke(null);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,enabled);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
 }
 private void requestDom(int n){
  var browser=TerminalBrowserSession.content();
  if(browser==null || TerminalBrowserSession.generation()!=domGeneration)return;
  long epoch=domGeneration;String marker="data-terminal-qa-"+n+"-"+(++domSerial);
  String js="(()=>{const value={stage:"+n+",generation:"+epoch+",url:location.href,defaultBinding:typeof window.cefQuery,terminalBinding:typeof window.muxiTerminalQuery,icons:[...document.querySelectorAll('#guideList img')].map(i=>({id:i.dataset.resource,width:i.naturalWidth,displayed:i.getClientRects().length>0&&!i.hidden,srcScheme:(i.getAttribute('src')||'').split(':')[0]})),tasks:document.querySelectorAll('#taskList [data-task]').length,error:document.querySelector('#taskMeta')?.textContent||'',list:document.querySelector('#taskList')?.textContent||''};document.documentElement.setAttribute('"+marker+"',btoa(unescape(encodeURIComponent(JSON.stringify(value)))));})()";
  browser.executeJavaScript(js,browser.getURL(),0);
  domRead=()->{if(stage!=n || TerminalBrowserSession.content()!=browser || TerminalBrowserSession.generation()!=epoch)return;browser.getSource(source->{
   if(stage!=n || TerminalBrowserSession.content()!=browser || TerminalBrowserSession.generation()!=epoch)return;
   var matcher=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(marker)+"=\\\"([^\\\"]+)\\\"").matcher(source);
   if(!matcher.find())return;
   try{dom=JsonParser.parseString(new String(Base64.getDecoder().decode(matcher.group(1)),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
   catch(Exception e){System.out.println("QA_DOM decode failed "+e);}
  });};domReadAt=ticks+3;
 }
}
