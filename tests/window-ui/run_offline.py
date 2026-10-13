"""Production TerminalScreen/layout with explicit MC/MCEF fixtures. No game launch."""
from pathlib import Path
import sys,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
out=Path(sys.argv[1]);jdk=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
files={
"com/cinemamod/mcef/MCEFBrowser.java":r"""package com.cinemamod.mcef;
public class MCEFBrowser {
 public boolean focus;public int width,height,presses,releases,moves,wheels,lastX,lastY;
 public java.util.List<Integer> keys=new java.util.ArrayList<>(),releasedKeys=new java.util.ArrayList<>();
 public void setFocus(boolean v){focus=v;}public void sendKeyPress(int k,int s,int m){keys.add(k);}
 public void sendKeyRelease(int k,int s,int m){releasedKeys.add(k);}public void sendKeyTyped(char c,int m){}
 public void sendMousePress(int x,int y,int b){presses++;lastX=x;lastY=y;}
 public void sendMouseRelease(int x,int y,int b){releases++;lastX=x;lastY=y;}
 public void sendMouseMove(int x,int y){moves++;lastX=x;lastY=y;}
 public void sendMouseWheel(int x,int y,double v,int m){wheels++;lastX=x;lastY=y;}
 public Renderer getRenderer(){return new Renderer();}public static class Renderer{public int getTextureID(){return 0;}}
}""",
"net/minecraft/network/chat/Component.java":"""package net.minecraft.network.chat;
public class Component {public String text;public Component(String t){text=t;}public String getString(){return text;}
 public static MutableComponent literal(String t){return new MutableComponent(t);}public static MutableComponent empty(){return literal("");}
 public static MutableComponent translatable(String s,Object...v){return literal(s+java.util.Arrays.toString(v));}
 public String toString(){return text;}}""",
"net/minecraft/network/chat/MutableComponent.java":"package net.minecraft.network.chat; public class MutableComponent extends Component{public MutableComponent(String s){super(s);}}",
"net/minecraft/client/Minecraft.java":"""package net.minecraft.client;
public class Minecraft {public static final Minecraft INSTANCE=new Minecraft();public Player player=new Player();public Object connection=new Object();public net.minecraft.client.gui.screens.Screen screen;public Window window=new Window();
 public static Minecraft getInstance(){return INSTANCE;}public Window getWindow(){return window;}public Object getConnection(){return connection;}
 public static class Player{public boolean isAlive(){return true;}}
 public static class Window{public double scale=1;public double getGuiScale(){return scale;}}
}""",
"net/minecraft/client/KeyMapping.java":"package net.minecraft.client;public class KeyMapping{public static int releases;public static void releaseAll(){releases++;}}",
"net/minecraft/client/gui/Font.java":"package net.minecraft.client.gui; public class Font{public String plainSubstrByWidth(String s,int width){return s;}}",
"net/minecraft/client/gui/GuiGraphics.java":"""package net.minecraft.client.gui;
public class GuiGraphics {public java.util.List<int[]> fills=new java.util.ArrayList<>();
 public void fill(int a,int b,int c,int d,int color){fills.add(new int[]{a,b,c,d,color});}public void drawString(Font f,String s,int x,int y,int c,boolean shadow){}
 public void flush(){}public Pose pose(){return new Pose();}public static class Pose{public Last last(){return new Last();}}public static class Last{public Object pose(){return null;}}}""",
"net/minecraft/client/gui/components/Tooltip.java":"package net.minecraft.client.gui.components;public class Tooltip{public net.minecraft.network.chat.Component text;public static Tooltip create(net.minecraft.network.chat.Component c){var t=new Tooltip();t.text=c;return t;}}",
"net/minecraft/client/gui/components/Button.java":r"""package net.minecraft.client.gui.components;
import net.minecraft.network.chat.*;import net.minecraft.client.gui.GuiGraphics;
public class Button{
 public interface OnPress{void onPress(Button b);}public interface CreateNarration{}
 protected static final CreateNarration DEFAULT_NARRATION=new CreateNarration(){};
 protected int x,y,w,h;public boolean active=true,focused;public Tooltip tooltip;protected OnPress press;
 protected Button(int x,int y,int w,int h,Component c,OnPress p,CreateNarration n){this.x=x;this.y=y;this.w=w;this.h=h;press=p;}
 public int getX(){return x;}public int getY(){return y;}public int getWidth(){return w;}public int getHeight(){return h;}
 public void setX(int a){x=a;}public void setY(int a){y=a;}public void setTooltip(Tooltip t){tooltip=t;}
 public void setFocused(boolean f){focused=f;}public boolean isFocused(){return focused;}
 public boolean contains(double a,double b){return a>=x&&b>=y&&a<x+w&&b<y+h;}
 public void onPress(){press.onPress(this);}protected void renderWidget(GuiGraphics g,int a,int b,float f){}
 public void render(GuiGraphics g,int a,int b,float f){renderWidget(g,a,b,f);}protected MutableComponent createNarrationMessage(){return Component.literal("");}
 public static Builder builder(Component c,OnPress p){return new Builder(c,p);}
 public static class Builder{Component c;OnPress p;int x,y,w,h;Tooltip t;Builder(Component c,OnPress p){this.c=c;this.p=p;}
 public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}public Builder tooltip(Tooltip t){this.t=t;return this;}
 public Button build(){Button b=new Button(x,y,w,h,c,p,DEFAULT_NARRATION);b.tooltip=t;return b;}}
}""",
"net/minecraft/client/gui/screens/Screen.java":r"""package net.minecraft.client.gui.screens;
import net.minecraft.client.*;import net.minecraft.client.gui.*;import net.minecraft.client.gui.components.Button;
public class Screen{
 public int width,height;protected Minecraft minecraft;protected Font font=new Font();public java.util.List<Button> widgets=new java.util.ArrayList<>();Button focused;
 public Screen(net.minecraft.network.chat.Component c){}public final void init(Minecraft m,int w,int h){minecraft=m;width=w;height=h;widgets.clear();init();setInitialFocus();}
 protected void init(){}protected void setInitialFocus(){}protected <T extends Button>T addRenderableWidget(T t){widgets.add(t);return t;}
 public void setFocused(Button b){if(focused!=null)focused.setFocused(false);focused=b;if(b!=null)b.setFocused(true);}
 public void resize(Minecraft m,int w,int h){init(m,w,h);}public boolean isPauseScreen(){return false;}
 public void onClose(){minecraft.screen=null;}public void removed(){}public void tick(){}
 public void renderBackground(GuiGraphics g,int x,int y,float f){}public void render(GuiGraphics g,int x,int y,float f){for(Button b:widgets)b.render(g,x,y,f);}
 public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;for(Button b:widgets)if(b.contains(x,y)){b.onPress();setFocused(b);return true;}return false;}
 public boolean mouseReleased(double x,double y,int b){return false;}public void mouseMoved(double x,double y){}
 public boolean mouseScrolled(double x,double y,double h,double v){return false;}
 public boolean keyPressed(int key,int scan,int modifiers){if((key==257||key==32)&&focused!=null){focused.onPress();return true;}
 if(key==258&&!widgets.isEmpty()){int i=widgets.indexOf(focused);setFocused(widgets.get((i+1)%widgets.size()));return true;}return false;}
 public boolean keyReleased(int a,int b,int c){return false;}public boolean charTyped(char c,int m){return false;}
}""",
"net/minecraft/client/renderer/GameRenderer.java":"package net.minecraft.client.renderer;public class GameRenderer{public static Object getPositionTexColorShader(){return null;}}",
"com/mojang/blaze3d/systems/RenderSystem.java":"package com.mojang.blaze3d.systems;public class RenderSystem{public static void disableDepthTest(){}public static void enableBlend(){}public static void defaultBlendFunc(){}public static void setShader(java.util.function.Supplier<?>s){}public static void setShaderTexture(int i,int t){}public static void enableDepthTest(){}}",
"com/mojang/blaze3d/vertex/BufferBuilder.java":"package com.mojang.blaze3d.vertex;public class BufferBuilder{public BufferBuilder addVertex(Object m,float x,float y,float z){return this;}public BufferBuilder setUv(float a,float b){return this;}public BufferBuilder setColor(int a,int b,int c,int d){return this;}public Object buildOrThrow(){return null;}}",
"com/mojang/blaze3d/vertex/BufferUploader.java":"package com.mojang.blaze3d.vertex;public class BufferUploader{public static void drawWithShader(Object b){}}",
"com/mojang/blaze3d/vertex/DefaultVertexFormat.java":"package com.mojang.blaze3d.vertex;public class DefaultVertexFormat{public static Object POSITION_TEX_COLOR;}",
"com/mojang/blaze3d/vertex/Tesselator.java":"package com.mojang.blaze3d.vertex;public class Tesselator{public static Tesselator getInstance(){return new Tesselator();}public BufferBuilder begin(Object a,Object b){return new BufferBuilder();}}",
"com/mojang/blaze3d/vertex/VertexFormat.java":"package com.mojang.blaze3d.vertex;public class VertexFormat{public enum Mode{QUADS}}",
"net/muxigame/terminal/client/music/TerminalMusicService.java":"package net.muxigame.terminal.client.music;public class TerminalMusicService{public static int closes;public static void closeLocal(){closes++;}}",
"net/muxigame/terminal/client/TerminalPassportNavigation.java":"package net.muxigame.terminal.client;public class TerminalPassportNavigation{public static int clears;public static void clear(){clears++;}}",
"net/muxigame/terminal/client/TerminalAlbumBridge.java":"package net.muxigame.terminal.client;public class TerminalAlbumBridge{public static boolean visible;public static void visibility(boolean v){visible=v;}}",
"net/muxigame/terminal/client/TerminalBrowserSession.java":r"""package net.muxigame.terminal.client;
import com.cinemamod.mcef.MCEFBrowser;
public class TerminalBrowserSession{
 public static MCEFBrowser shell,app;public static int resizes,barWidth,backCalls,homeCalls;
 public static boolean owns(MCEFBrowser b){return b==shell||b==app;}public static MCEFBrowser activeBrowser(){return app==null?shell:app;}public static MCEFBrowser content(){return app;}
 public static void resizeViews(int w,int h,int bar){resizes++;barWidth=bar;shell.width=w;shell.height=h;if(app!=null){app.width=w;app.height=h-bar;}}
 public static void back(){backCalls++;}public static void requestHome(){homeCalls++;}public static boolean contentVisible(){return false;}
 public static Motion contentMotion(){return new Motion();}public static State state(){return new State();}public static void finishContentFrame(){}
 public static class Motion{public float scale(){return 1;}public float alpha(){return 1;}}
 public static class State{public String url(){return "";}public String title(){return "";}public String error(){return "";}public boolean loading(){return false;}}
}""",
"net/muxigame/terminal/client/WindowUiTest.java":r"""package net.muxigame.terminal.client;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import com.cinemamod.mcef.MCEFBrowser;
public class WindowUiTest{
 static int checks;static void check(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 static TerminalWindowLayout.View view(TerminalScreen s)throws Exception{var f=TerminalScreen.class.getDeclaredField("windowLayout");f.setAccessible(true);return (TerminalWindowLayout.View)f.get(s);}
 static boolean full(TerminalScreen s)throws Exception{var f=TerminalScreen.class.getDeclaredField("fullscreen");f.setAccessible(true);return f.getBoolean(s);}
 static TerminalScreen open(int w,int h,double dpi){var mc=Minecraft.getInstance();mc.window.scale=dpi;var s=new TerminalScreen(TerminalBrowserSession.shell);mc.screen=s;s.init(mc,w,h);return s;}
 static void toggle(TerminalScreen s)throws Exception{var b=view(s).windowMode();check(s.mouseClicked(b.x()+9,b.y()+9,0),"mode click consumed");s.tick();}
 static void reset(){TerminalBrowserSession.shell=new MCEFBrowser();TerminalBrowserSession.app=null;TerminalBrowserSession.resizes=0;TerminalPassportNavigation.clears=0;net.muxigame.terminal.client.music.TerminalMusicService.closes=0;}
 static void bounds(TerminalWindowLayout.View v,int w,int h){
 check(v.outer().x()>=0&&v.outer().y()>=0&&v.outer().right()<=w&&v.outer().bottom()<=h,"outer remains in viewport");
 check(Math.abs(v.outer().width()-v.outer().height()*1.6)<=1,"16:10 aspect within pixel rounding");
 check(v.close().right()<=v.outer().right()&&v.windowMode().x()>=v.outer().x(),"buttons remain inside frame");
 check(v.windowMode().right()<v.close().x(),"window button sits left of close with gap");
 check(v.windowMode().bottom()<v.content().y()&&!v.content().contains(v.close().x(),v.close().y()),"chrome separate from web hit box");
 }
 static void geometry(){
 int[][] sizes={{1920,1080},{960,540},{640,360},{480,270},{320,240},{427,240},{3440,1440},{400,700},{160,90}};
 for(int[] sz:sizes){var a=TerminalWindowLayout.fit(sz[0],sz[1],false);var b=TerminalWindowLayout.fit(sz[0],sz[1],true);
 bounds(a,sz[0],sz[1]);bounds(b,sz[0],sz[1]);check(b.outer().width()>=a.outer().width()&&b.outer().height()>=a.outer().height(),"maximize never shrinks");
 if(sz[0]>=320){int ow=Math.max(180,(int)(sz[0]*.58f)),oh=(int)Math.round(ow/1.6),limit=Math.max(120,(int)(sz[1]*.54f));if(oh>limit){oh=limit;ow=(int)Math.round(oh*1.6);}
 check(Math.abs(a.outer().width()-ow*1.1)<=2&&Math.abs(a.outer().height()-oh*1.1)<=2,"normal outer dimensions increased 10 percent");}
 for(double dpi:new double[]{1,1.5,2,3,4}){var c=a.content();check(a.browserX(c.x(),dpi)==0&&a.browserY(c.y(),dpi,0)==0,"root top-left maps to zero");
 check(a.browserX(c.right()-.01,dpi)<a.browserWidth(dpi)&&a.browserY(c.bottom()-.01,dpi,0)<a.browserHeight(dpi),"inside right/bottom never maps outside pixel buffer");
 check(a.browserY(c.y()+22,dpi,22)==0&&a.browserY(c.y()+21,dpi,22)<0,"child toolbar origin and negative toolbar preserved");
 check(!c.contains(c.right(),c.y())&&!c.contains(c.x(),c.bottom()),"right/bottom hit boundaries exclusive");}
 }
 }
 static void screen(int w,int h,double dpi)throws Exception{
 reset();var shell=TerminalBrowserSession.shell;var s=open(w,h,dpi);var normal=view(s);
 check(!full(s)&&s.widgets.size()==2,"reopen defaults normal with exactly two native controls");
 check(shell.width==normal.browserWidth(dpi)&&shell.height==normal.browserHeight(dpi),"normal browser resized in physical pixels");
 check(s.widgets.get(1).tooltip.text.getString().contains("全屏"),"mode tooltip initially fullscreen");
 int before=shell.presses;toggle(s);var max=view(s);check(full(s),"mouse toggles fullscreen");bounds(max,w,h);
 check(shell.presses==before&&TerminalBrowserSession.shell==shell,"chrome click does not reach or replace browser");
 check(shell.width==max.browserWidth(dpi)&&shell.height==max.browserHeight(dpi),"fullscreen pixel buffer follows geometry");
 check(s.widgets.get(1).tooltip.text.getString().contains("还原"),"mode tooltip switches to restore");
 int[] bg=new int[4];var g=new GuiGraphics();s.render(g,0,0,0);var first=g.fills.getFirst();
 check(first[0]==max.outer().x()&&first[1]==max.outer().y()&&first[2]==max.outer().right()&&first[3]==max.outer().bottom(),"actual Screen frame matches layout");
 check(g.fills.stream().filter(x->x[4]==0xffffffff).count()==8,"restore icon draws two pixel outlines");
 var c=max.content();s.mouseClicked(c.x()+10,c.y()+11,0);
 check(shell.lastX==Math.round(10*dpi)&&shell.lastY==Math.round(11*dpi),"root page click reaches correct pixel");
 check(shell.focus,"content click restores browser keyboard focus");
 s.keyPressed(65,0,0);check(shell.keys.contains(65),"web key captured before mode switch");toggle(s);
 check(!full(s)&&view(s).equals(normal),"restore returns exact enlarged normal bounds");
 check(shell.releasedKeys.contains(65),"mode switch releases held web key");
 check(!shell.focus,"native control focus keeps keyboard out of web");
 s.keyPressed(66,0,0);check(!shell.keys.contains(66),"native button does not leak key to web");
 s.keyPressed(257,0,0);s.tick();check(full(s),"native Enter activates focused window control");
 s.resize(Minecraft.getInstance(),w-20,h-10);check(full(s),"fullscreen retained across GUI resize");
 var resized=view(s);check(s.widgets.size()==2&&s.widgets.get(0).getX()==resized.close().x()&&s.widgets.get(1).getX()==resized.windowMode().x(),"resize recreates exactly two controls at new hit boxes");
 s.keyPressed(295,0,0);s.tick();check(s.closeButton.isFocused()&&!shell.focus,"F6 preserves native close focus");
 s.keyPressed(295,0,0);check(!s.closeButton.isFocused()&&shell.focus,"F6 returns to browser");
 TerminalBrowserSession.app=new MCEFBrowser();s.resize(Minecraft.getInstance(),w,h);var app=TerminalBrowserSession.app;var l=view(s);c=l.content();
 s.mouseClicked(c.x()+90,c.y()+22+7,0);check(app.lastX==Math.round(90*dpi)&&app.lastY==Math.round(7*dpi),"child page hit maps below native toolbar");
 check(app.width==shell.width&&app.height==shell.height-Math.round(22*dpi),"child physical size excludes toolbar");
 int appPress=app.presses;s.mouseClicked(c.x()+5,c.y()+5,0);check(app.presses==appPress&&TerminalBrowserSession.backCalls>0,"toolbar handled natively without browser press");
 var close=l.close();s.mouseClicked(close.x()+9,close.y()+9,0);
 check(Minecraft.getInstance().screen==null&&shell.width==640&&shell.height==400,"close exits and restores original held viewport");
 check(net.muxigame.terminal.client.music.TerminalMusicService.closes==1&&TerminalPassportNavigation.clears==1,"existing close cleanup preserved");
 var again=open(w,h,dpi);check(!full(again)&&view(again).equals(normal)&&TerminalBrowserSession.shell==shell,"close/reopen normal enlarged layout with same browser identity");
 }
 public static void main(String[]args)throws Exception{geometry();for(double dpi:new double[]{1,1.5,2,3,4})for(int[] s:new int[][]{{960,540},{640,360},{480,270},{320,240}})screen(s[0],s[1],dpi);
 System.out.println("{\"passed\":true,\"checks\":"+checks+",\"scope\":\"production layout, Screen and key capture; explicit MC/MCEF fixtures; no native MC\"}");}
}""",
}
for name,code in files.items():
 p=out/"fixtures"/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding="utf-8")
sources=list((out/"fixtures").rglob("*.java"))+[ROOT/"src/main/java/net/muxigame/terminal/client"/n for n in ["TerminalWindowLayout.java","TerminalScreen.java","TerminalKeyCapture.java"]]
classes=out/"classes";classes.mkdir(exist_ok=True)
args=["--release","21","-encoding","UTF-8","-proc:none","-d",str(classes),*map(str,sources)]
(out/"compile.args").write_text("\n".join('"'+x.replace("\\","/")+'"' for x in args),encoding="utf-8")
subprocess.run([str(jdk/"bin/javac.exe"),"@"+str(out/"compile.args")],check=True)
result=subprocess.run([str(jdk/"bin/java.exe"),"-cp",str(classes),"net.muxigame.terminal.client.WindowUiTest"],capture_output=True,text=True)
print(result.stdout,end="");print(result.stderr,end="",file=sys.stderr)
if result.returncode:raise SystemExit(result.returncode)
data=json.loads(result.stdout);(out/"result.json").write_text(json.dumps(data,indent=2)+"\n",encoding="utf-8")
