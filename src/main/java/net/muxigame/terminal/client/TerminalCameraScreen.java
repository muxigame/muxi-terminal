package net.muxigame.terminal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Transparent native viewfinder. MC widgets/fonts; no browser, tint, blur or secondary world pass. */
public final class TerminalCameraScreen extends Screen {
    private Button shutter,lens;
    private String error="";
    public TerminalCameraScreen(){super(Component.literal("终端相机"));}
    @Override protected void init(){
        int w=Math.max(60,Math.min(112,(width-32)/3)),x=(width-(w*3+16))/2,y=height-42;
        shutter=addRenderableWidget(Button.builder(Component.literal("拍照 [空格]"),b->act(TerminalCamera::shutter)).bounds(x,y,w,24).build());
        lens=addRenderableWidget(Button.builder(Component.literal("切换自拍 [F]"),b->toggle()).bounds(x+w+8,y,w,24).build());
        addRenderableWidget(Button.builder(Component.literal("关闭相机 [Esc]"),b->onClose()).bounds(x+(w+8)*2,y,w,24).build());
    }
    private void act(Runnable action){try{action.run();error="";}catch(RuntimeException failed){error=failed.getMessage()==null?"相机暂不可用":failed.getMessage();}}
    private void toggle(){act(()->TerminalCamera.mode(!"selfie".equals(TerminalCamera.state().mode())));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        TerminalCamera.capture();
        var s=TerminalCamera.state();boolean focused=TerminalCamera.focused();
        shutter.active=s.active() && !s.busy() && focused;lens.active=s.active() && !s.busy() && focused;
        lens.setMessage(Component.literal("selfie".equals(s.mode())?"切换前拍 [F]":"切换自拍 [F]"));
        int a=Math.max(12,width/12),b=Math.max(12,height/10),right=width-a,bottom=height-56,length=20,color=0xDFFFF4D5;
        g.fill(a,b,a+length,b+2,color);g.fill(a,b,a+2,b+length,color);
        g.fill(right-length,b,right,b+2,color);g.fill(right-2,b,right,b+length,color);
        g.fill(a,bottom-2,a+length,bottom,color);g.fill(a,bottom-length,a+2,bottom,color);
        g.fill(right-length,bottom-2,right,bottom,color);g.fill(right-2,bottom-length,right,bottom,color);
        String status=!error.isEmpty()?error:!focused?"返回游戏窗口继续拍照":s.busy()?"正在保存到本机…":!s.error().isEmpty()?s.error():!s.saved().isEmpty()?"已保存 · 关闭后可在相册查看":"拖动空白画面调整取景 · 照片仅存本机";
        g.drawCenteredString(font,status,width/2,Math.max(4,height-58),0xFFF4D5);
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256){onClose();return true;}
        if(key==32){act(TerminalCamera::shutter);return true;}
        if(key==70){toggle();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0 && getFocused()==null){act(()->TerminalCamera.aim(dx,dy));return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        setFocused(null);return button==0;
    }
    @Override public void tick(){if(!TerminalCamera.state().active())onClose();}
    @Override public void onClose(){
        if(net.minecraft.client.Minecraft.getInstance().screen!=this)return;
        TerminalCamera.stop();
        // Defensive safe exit if a screen was constructed without a resumable terminal session.
        if(net.minecraft.client.Minecraft.getInstance().screen==this)net.minecraft.client.Minecraft.getInstance().setScreen(null);
    }
    @Override public void removed(){TerminalCamera.removed(this);super.removed();}
}
