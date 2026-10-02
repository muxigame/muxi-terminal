package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;

/** Full-screen tablet shell around one persistent MCEF browser session. */
public final class TerminalScreen extends Screen {
    private static final int FRAME = 10;
    private static final int TOP = 12;
    private static final int APP_BAR = 22;
    private final MCEFBrowser browser;
    private int left, top, contentWidth, contentHeight;

    public TerminalScreen(MCEFBrowser browser) {
        super(Component.literal("玩家终端"));
        this.browser = browser;
    }

    @Override
    protected void init() {
        super.init();
        layout();
        browser.setFocus(true);
    }

    private void layout() {
        int availableWidth = Math.max(180, (int)(width * 0.58f));
        int availableHeight = Math.max(120, (int)(height * 0.54f));
        double ratio = 16.0 / 10.0;
        int outerWidth = availableWidth;
        int outerHeight = (int) Math.round(outerWidth / ratio);
        if (outerHeight > availableHeight) {
            outerHeight = availableHeight;
            outerWidth = (int) Math.round(outerHeight * ratio);
        }
        left = (width - outerWidth) / 2 + FRAME;
        top = (height - outerHeight) / 2 + TOP;
        contentWidth = Math.max(64, outerWidth - FRAME * 2);
        contentHeight = Math.max(64, outerHeight - TOP - FRAME);
        resizeBrowser();
    }

    private void resizeBrowser() {
        if (minecraft == null) return;
        double scale = minecraft.getWindow().getGuiScale();
        TerminalBrowserSession.resizeViews(Math.max(1, (int) Math.round(contentWidth * scale)),
            Math.max(1, (int) Math.round(contentHeight * scale)), Math.max(1,(int)Math.round(APP_BAR*scale)));
    }

    private int browserX(double x) {
        return (int) Math.round((x - left) * minecraft.getWindow().getGuiScale());
    }

    private int browserY(double y) {
        return (int) Math.round((y - top - (TerminalBrowserSession.activeBrowser()==browser?0:APP_BAR)) * minecraft.getWindow().getGuiScale());
    }

    private boolean inside(double x, double y) {
        return x >= left && y >= top && x < left + contentWidth && y < top + contentHeight;
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        layout();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        TerminalPassportNavigation.clear();
        TerminalBrowserSession.closeContent();
        browser.setFocus(false);
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The terminal is a physical tablet, not a fullscreen shader overlay.
        // Avoid tinting the world behind it.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int x0 = left - FRAME, y0 = top - TOP;
        int x1 = left + contentWidth + FRAME, y1 = top + contentHeight + FRAME;
        graphics.fill(x0, y0, x1, y1, 0xFF17120D);
        graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFF6A4B28);
        graphics.fill(left - 2, top - 2, left + contentWidth + 2, top + contentHeight + 2, 0xFF080B10);
        graphics.fill(x0 + 7, y0 + 7, x0 + 11, y0 + 11, 0xFF64D7E8);
        graphics.drawString(font,"玩家终端",x0+16,y0+5,0xFFFFD45A,false);
        graphics.drawString(font,"⌂",x1-18,y0+5,0xFF9DAAB5,false);

        graphics.flush();
        drawBrowser(graphics,browser,left,top,contentWidth,contentHeight,1);
        var content=TerminalBrowserSession.content();
        if(content!=null){
            if(TerminalBrowserSession.contentVisible()){
                var frame=TerminalBrowserSession.contentMotion();
                float w=contentWidth*frame.scale(),h=(contentHeight-APP_BAR)*frame.scale();
                drawBrowser(graphics,content,left+(contentWidth-w)/2,top+APP_BAR+(contentHeight-APP_BAR-h)/2,w,h,frame.alpha());
            }
            var state=TerminalBrowserSession.state();
            graphics.fill(left,top,left+contentWidth,top+APP_BAR,0xFFB6AD94);
            graphics.drawString(font,"‹ 返回",left+4,top+6,0xFF302A20,false);
            graphics.drawString(font,"⌂ 主页",left+44,top+6,0xFF302A20,false);
            String target=state.url();
            try{var uri=java.net.URI.create(target);target=uri.getHost()==null?state.title():uri.getHost()+(uri.getPort()<0?"":":"+uri.getPort());}catch(IllegalArgumentException ignored){}
            String label=state.title()+" · "+target+(state.loading()?" · 加载中":"");
            graphics.drawString(font,font.plainSubstrByWidth(label,Math.max(1,contentWidth-94)),left+88,top+6,0xFF302A20,false);
            if(!state.error().isEmpty()){
                graphics.fill(left,top+APP_BAR,left+contentWidth,top+APP_BAR+22,0xFFEDD2AC);
                graphics.drawString(font,font.plainSubstrByWidth(state.error()+"；可点击主页返回",contentWidth-8),left+4,top+APP_BAR+6,0xFF7C2919,false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        TerminalBrowserSession.finishContentFrame();
    }

    private void drawBrowser(GuiGraphics graphics,MCEFBrowser view,float x,float y,float w,float h,float alpha) {
        int texture = view.getRenderer().getTextureID();
        if (texture == 0) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = graphics.pose().last().pose();
        float x0 = x, y0 = y, x1 = x+w, y1 = y+h;
        int opacity=Math.round(Math.max(0,Math.min(1,alpha))*255);
        b.addVertex(matrix, x0, y1, 0).setUv(0, 1).setColor(255,255,255,opacity);
        b.addVertex(matrix, x1, y1, 0).setUv(1, 1).setColor(255,255,255,opacity);
        b.addVertex(matrix, x1, y0, 0).setUv(1, 0).setColor(255,255,255,opacity);
        b.addVertex(matrix, x0, y0, 0).setUv(0, 0).setColor(255,255,255,opacity);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int x0=left-FRAME, y0=top-TOP, x1=left+contentWidth+FRAME;
        if(button==0 && y>=y0 && y<top && x>=x1-34 && x<x1) {
            TerminalBrowserSession.requestHome();
            return true;
        }
        if(button==0 && TerminalBrowserSession.content()!=null && y>=top && y<top+APP_BAR && x>=left && x<left+contentWidth){
            if(x<left+40)TerminalBrowserSession.back();
            else if(x<left+84)TerminalBrowserSession.requestHome();
            return true;
        }
        if (inside(x, y)) {
            var target=TerminalBrowserSession.activeBrowser();
            if(TerminalBrowserSession.content()!=null && y<top+APP_BAR)return true;
            target.sendMousePress(browserX(x), browserY(y), button);
            target.setFocus(true);
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (inside(x, y) && browserY(y)>=0) TerminalBrowserSession.activeBrowser().sendMouseRelease(browserX(x), browserY(y), button);
        return super.mouseReleased(x, y, button);
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (inside(x, y) && browserY(y)>=0) TerminalBrowserSession.activeBrowser().sendMouseMove(browserX(x), browserY(y));
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inside(x, y) && browserY(y)>=0) TerminalBrowserSession.activeBrowser().sendMouseWheel(browserX(x), browserY(y), vertical, 0);
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode==256 /* GLFW_KEY_ESCAPE */ && TerminalBrowserSession.content()!=null){TerminalBrowserSession.requestHome();return true;}
        TerminalBrowserSession.activeBrowser().sendKeyPress(keyCode, scanCode, modifiers);
        TerminalBrowserSession.activeBrowser().setFocus(true);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        TerminalBrowserSession.activeBrowser().sendKeyRelease(keyCode, scanCode, modifiers);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        TerminalBrowserSession.activeBrowser().sendKeyTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }
}

