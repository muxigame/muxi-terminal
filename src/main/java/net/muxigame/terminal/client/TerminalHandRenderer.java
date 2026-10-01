package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Matrix4f;

import static com.mojang.math.Axis.XP;
import static com.mojang.math.Axis.YP;
import static com.mojang.math.Axis.ZP;

/** First-person map-style terminal: both arms support one centered live display. */
final class TerminalHandRenderer {
    private final Minecraft mc = Minecraft.getInstance();

    void render(PoseStack stack, MultiBufferSource buffers, int light, float swing, float equip) {
        float sqrt = (float)Math.sqrt(swing);
        float sway = (float)Math.sin(sqrt * Math.PI);
        float bob = (float)Math.sin(sqrt * Math.PI * 2.0);

        boolean secondHand = mc.player != null && mc.player.getOffhandItem().isEmpty();
        renderArm(stack, buffers, light, -1f, equip, sway, bob);
        if (secondHand) renderArm(stack, buffers, light, 1f, equip, sway, bob);

        stack.pushPose();
        stack.translate(-0.58f, -0.42f - equip * 0.6f + bob * 0.04f, -0.88f - sway * 0.08f);
        stack.mulPose(XP.rotationDegrees(13f + sway * 5f));
        stack.mulPose(ZP.rotationDegrees(-sway * 2f));
        stack.scale(0.92f, 0.92f, 0.92f);

        drawFrame(stack.last().pose());
        MCEFBrowser browser = TerminalBrowserSession.current();
        if (browser != null && browser.getRenderer().getTextureID() != 0) drawScreen(stack.last().pose(), browser);
        else drawStandby(stack.last().pose());

        stack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private void renderArm(PoseStack stack, MultiBufferSource buffers, int light, float side,
                           float equip, float sway, float bob) {
        if (mc.player == null) return;
        stack.pushPose();
        stack.translate(side * 0.66f, -0.62f - equip * 0.55f + bob * 0.03f, -0.72f - sway * 0.05f);
        stack.mulPose(YP.rotationDegrees(side * (38f + sway * 8f)));
        stack.mulPose(ZP.rotationDegrees(side * 110f));
        stack.mulPose(XP.rotationDegrees(195f));
        stack.translate(side * 4.7f, 0.25f, 0.1f);
        PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
        if (side > 0) renderer.renderRightHand(stack, buffers, light, mc.player);
        else renderer.renderLeftHand(stack, buffers, light, mc.player);
        stack.popPose();
    }

    private static void drawFrame(Matrix4f matrix) {
        // The tablet is a held object, not a fullscreen overlay. Keep the depth buffer
        // active so clouds/particles behind the player cannot leak through it.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(b, matrix, 0, 0, 1.05f, 0.63f, -0.010f, 18, 22, 29, 255);
        quad(b, matrix, 0.035f, 0.035f, 1.125f, 0.665f, -0.015f, 43, 51, 62, 255);
        quad(b, matrix, 0.065f, 0.070f, 1.095f, 0.625f, -0.020f, 5, 8, 12, 255);
        quad(b, matrix, 0.075f, 0.646f, 0.105f, 0.665f, -0.025f, 100, 215, 232, 255);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void drawStandby(Matrix4f matrix) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(b, matrix, 0.075f, 0.080f, 1.085f, 0.615f, -0.030f, 13, 22, 30, 255);
        quad(b, matrix, 0.47f, 0.31f, 0.69f, 0.385f, -0.032f, 56, 142, 157, 255);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void drawScreen(Matrix4f matrix, MCEFBrowser browser) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float x0=.075f, y0=.080f, x1=1.085f, y1=.615f, z=-.031f;
        b.addVertex(matrix,x0,y0,z).setUv(0,1).setColor(255,255,255,255);
        b.addVertex(matrix,x1,y0,z).setUv(1,1).setColor(255,255,255,255);
        b.addVertex(matrix,x1,y1,z).setUv(1,0).setColor(255,255,255,255);
        b.addVertex(matrix,x0,y1,z).setUv(0,0).setColor(255,255,255,255);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderTexture(0, 0);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0,float y0,float x1,float y1,float z,
                             int r,int g,int bl,int a) {
        b.addVertex(m,x0,y0,z).setColor(r,g,bl,a);
        b.addVertex(m,x1,y0,z).setColor(r,g,bl,a);
        b.addVertex(m,x1,y1,z).setColor(r,g,bl,a);
        b.addVertex(m,x0,y1,z).setColor(r,g,bl,a);
    }
}
