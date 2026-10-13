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

/** Resizable tablet shell around the persistent MCEF browser session. */
public class TerminalScreen extends Screen {
    private static final int FRAME = 10;
    private static final int TOP = 24;
    private static final int APP_BAR = 22;
    protected final MCEFBrowser browser;
    protected net.minecraft.client.gui.components.Button closeButton;
    private final TerminalKeyCapture<MCEFBrowser> keyCapture = new TerminalKeyCapture<>(new TerminalKeyCapture.Channel<>() {
        public boolean usable(MCEFBrowser target) { return TerminalBrowserSession.owns(target); }
        public void focus(MCEFBrowser target, boolean value) { target.setFocus(value); }
        public void press(MCEFBrowser target, int key, int scan, int modifiers) { target.sendKeyPress(key, scan, modifiers); }
        public void release(MCEFBrowser target, int key, int scan, int modifiers) { target.sendKeyRelease(key, scan, modifiers); }
        public void type(MCEFBrowser target, char character, int modifiers) { target.sendKeyTyped(character, modifiers); }
    });
    private int left, top, contentWidth, contentHeight;
    private boolean fullscreen;
    private TerminalWindowLayout.View windowLayout;
    private WindowModeButton windowModeButton;

    public TerminalScreen(MCEFBrowser browser) {
        super(Component.literal("玩家终端"));
        this.browser = browser;
    }

    @Override
    protected void init() {
        TerminalAlbumBridge.visibility(true);
        super.init();
        layout();
        closeButton = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            Component.literal("X"), button -> onClose()).bounds(left + contentWidth - 10, top - TOP + 3, 18, 18)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Esc 退出并保留页面；F6 聚焦关闭按钮"))).build());
        windowModeButton = addRenderableWidget(new WindowModeButton());
        positionWindowControls();
        focusBrowser();
    }

    @Override
    protected void setInitialFocus() {
        setFocused(null);
        if (closeButton != null) closeButton.setFocused(false);
        if (windowModeButton != null) windowModeButton.setFocused(false);
        focusBrowser();
    }

    private void layout() {
        windowLayout = TerminalWindowLayout.fit(width, height, fullscreen);
        var content = windowLayout.content();
        left = content.x();
        top = content.y();
        contentWidth = content.width();
        contentHeight = content.height();
        positionWindowControls();
        resizeBrowser();
    }

    private void positionWindowControls() {
        if (windowLayout == null) return;
        if (closeButton != null) {
            closeButton.setX(windowLayout.close().x());
            closeButton.setY(windowLayout.close().y());
        }
        if (windowModeButton != null) {
            windowModeButton.setX(windowLayout.windowMode().x());
            windowModeButton.setY(windowLayout.windowMode().y());
            windowModeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(fullscreen ? "还原终端窗口" : "全屏终端窗口")));
        }
    }

    private void toggleFullscreen() {
        releaseBrowserKeys();
        fullscreen = !fullscreen;
        layout();
        focusBrowser();
    }

    private boolean windowControlFocused() {
        return (closeButton != null && closeButton.isFocused())
            || (windowModeButton != null && windowModeButton.isFocused());
    }

    /** Native MC background with pixel icons independent of font glyph coverage. */
    private final class WindowModeButton extends net.minecraft.client.gui.components.Button {
        WindowModeButton() {
            super(0, 0, 18, 18, Component.empty(), button -> toggleFullscreen(), DEFAULT_NARRATION);
        }
        @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return Component.translatable("gui.narrate.button", Component.literal(fullscreen ? "还原" : "全屏"));
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.renderWidget(graphics, mouseX, mouseY, delta);
            int color = active ? 0xffffffff : 0xffa0a0a0;
            int x = getX(), y = getY();
            if (fullscreen) {
                outline(graphics, x + 6, y + 4, 8, 8, color);
                outline(graphics, x + 4, y + 6, 8, 8, color);
            } else outline(graphics, x + 5, y + 5, 8, 8, color);
        }
        private void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
            graphics.fill(x, y, x + w, y + 1, color);
            graphics.fill(x, y + h - 1, x + w, y + h, color);
            graphics.fill(x, y, x + 1, y + h, color);
            graphics.fill(x + w - 1, y, x + w, y + h, color);
        }
    }

    private void resizeBrowser() {
        if (minecraft == null) return;
        double scale = minecraft.getWindow().getGuiScale();
        TerminalBrowserSession.resizeViews(windowLayout.browserWidth(scale),
            windowLayout.browserHeight(scale), Math.max(1,(int)Math.round(APP_BAR*scale)));
    }

    private int browserX(double x) {
        return windowLayout.browserX(x, minecraft.getWindow().getGuiScale());
    }

    private int browserY(double y) {
        return windowLayout.browserY(y, minecraft.getWindow().getGuiScale(), TerminalBrowserSession.activeBrowser()==browser?0:APP_BAR);
    }

    private boolean inside(double x, double y) {
        return windowLayout.content().contains(x, y);
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
        net.muxigame.terminal.client.music.TerminalMusicService.closeLocal();
        TerminalPassportNavigation.clear();
        releaseBrowserKeys();
        net.minecraft.client.KeyMapping.releaseAll();
        TerminalBrowserSession.resizeViews(640, 400, 32);
        super.onClose();
    }

    @Override
    public void removed() {
        // A replaced screen must also disarm retained account cookies and pending proof.
        TerminalPassportNavigation.clear();
        TerminalAlbumBridge.visibility(false);
        releaseBrowserKeys();
        net.minecraft.client.KeyMapping.releaseAll();
        super.removed();
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.getConnection() == null) { onClose(); return; }
        focusBrowser();
    }

    private void releaseBrowserKeys() { keyCapture.clear(); }

    protected final MCEFBrowser focusBrowser() {
        MCEFBrowser next = TerminalBrowserSession.activeBrowser();
        keyCapture.bind(next, !windowControlFocused());
        return next;
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
        if(button==0 && TerminalBrowserSession.content()!=null && y>=top && y<top+APP_BAR && x>=left && x<left+contentWidth){
            if(x<left+40)TerminalBrowserSession.back();
            else if(x<left+84)TerminalBrowserSession.requestHome();
            return true;
        }
        if (inside(x, y)) {
            var target=TerminalBrowserSession.activeBrowser();
            if(TerminalBrowserSession.content()!=null && y<top+APP_BAR)return true;
            setFocused(null);
            closeButton.setFocused(false);
            windowModeButton.setFocused(false);
            focusBrowser().sendMousePress(browserX(x), browserY(y), button);
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
        if (keyCode == 256 || (keyCode == 87 && (modifiers & 2) != 0)) { onClose(); return true; }
        if (keyCode == 295) { // F6 keeps the existing shortcut to the permanent close button.
            boolean focused = !closeButton.isFocused();
            windowModeButton.setFocused(false);
            setFocused(focused ? closeButton : null);
            closeButton.setFocused(focused);
            focusBrowser();
            return true;
        }
        if (windowControlFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        focusBrowser();
        if (keyCode == 263 && (modifiers & 4) != 0) { // Alt+Left is native navigation, including external pages.
            TerminalBrowserSession.back();
            return true;
        }
        keyCapture.press(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        keyCapture.release(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        focusBrowser();
        keyCapture.type(codePoint, modifiers);
        return true;
    }
}
