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

    void render(PoseStack stack, MultiBufferSource buffers, int light, float swing, float equip, boolean mainHand) {
        float sqrt = (float)Math.sqrt(swing);
        float sway = (float)Math.sin(sqrt * Math.PI);
        float bob = (float)Math.sin(sqrt * Math.PI * 2.0);

        // Only use the second arm when the terminal is actually in one hand and
        // the other hand is free. A previous implementation only checked the
        // offhand, which made main-hand and offhand holding behave differently.
        boolean terminalMain = mc.player != null && mc.player.getMainHandItem().is(net.muxigame.terminal.MuxiTerminal.PLAYER_TERMINAL.get());
        boolean terminalOff = mc.player != null && mc.player.getOffhandItem().is(net.muxigame.terminal.MuxiTerminal.PLAYER_TERMINAL.get());
        boolean secondHand = (terminalMain && mc.player.getOffhandItem().isEmpty())
            || (terminalOff && mc.player.getMainHandItem().isEmpty());
        boolean rightHand = mainHand == (mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        float heldSide = rightHand ? 1f : -1f;
        if (secondHand) {
            renderArm(stack, buffers, light, -1f, equip, sway, bob, true);
            renderArm(stack, buffers, light, 1f, equip, sway, bob, true);
        } else {
            renderArm(stack, buffers, light, heldSide, equip, sway, bob, false);
        }

        stack.pushPose();
        if (secondHand) {
            // Two-handed tablet: centered, slightly farther away and smaller.
            stack.translate(-0.48f, -0.35f - equip * 0.55f + bob * 0.03f, -0.96f - sway * 0.06f);
            stack.mulPose(XP.rotationDegrees(11f + sway * 4f));
            stack.mulPose(ZP.rotationDegrees(-sway * 1.5f));
            stack.scale(0.78f, 0.78f, 0.78f);
        } else {
            // One-handed tablet: sit toward the hand instead of pretending a
            // second hand is supporting the opposite edge.
            stack.translate(rightHand ? -0.28f : -0.62f,
                -0.40f - equip * 0.55f + bob * 0.035f,
                -0.94f - sway * 0.06f);
            stack.mulPose(XP.rotationDegrees(12f + sway * 4f));
            stack.mulPose(YP.rotationDegrees(rightHand ? -5f : 5f));
            stack.mulPose(ZP.rotationDegrees((rightHand ? -4f : 4f) - sway * 1.5f));
            stack.scale(0.68f, 0.68f, 0.68f);
        }

        drawFrame(stack.last().pose());
        MCEFBrowser browser = TerminalBrowserSession.current();
        if (browser != null && browser.getRenderer().getTextureID() != 0) drawScreen(stack.last().pose(), browser);
        else drawStandby(stack.last().pose());

        stack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private void renderArm(PoseStack stack, MultiBufferSource buffers, int light, float side,
                           float equip, float sway, float bob, boolean twoHanded) {
        if (mc.player == null) return;
        stack.pushPose();
        // Include the map renderer's outer rotations: the hand-local transform
        // alone points the arms below the camera instead of toward the tablet.
        stack.translate(side * (twoHanded ? .12f : .26f),
            -.10f - equip * .55f + bob * .03f, -1.25f - sway * .05f);
        stack.mulPose(XP.rotationDegrees(-85f));
        stack.mulPose(YP.rotationDegrees(90f));
        stack.mulPose(YP.rotationDegrees(92f));
        stack.mulPose(XP.rotationDegrees(45f));
        stack.mulPose(ZP.rotationDegrees(side * -41f));
        stack.translate(side * .30f, -1.10f, .45f);
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
        // A shallow closed box instead of several coplanar sheets. The side
        // walls remain visible at an angle and make the terminal read as a pad.
        // Camera looks down -Z: the visible front is the larger local Z.
        float x0=0f,y0=0f,x1=1.16f,y1=.70f,front=.030f,back=-.045f;
        quad(b, matrix, x0,y0,x1,y1,back, 29,33,37,255);
        sideX(b,matrix,x0,y0,y1,front,back, 42,47,51,255);
        sideX(b,matrix,x1,y0,y1,back,front, 14,17,20,255);
        sideY(b,matrix,y0,x0,x1,back,front, 16,19,22,255);
        sideY(b,matrix,y1,x0,x1,front,back, 47,52,55,255);
        quad(b, matrix, x0,y0,x1,y1,front, 31,36,40,255);
        quad(b, matrix, .035f,.035f,1.125f,.665f,.034f, 70,76,78,255);
        quad(b, matrix, .065f,.070f,1.095f,.625f,.038f, 5,8,12,255);
        quad(b, matrix, .075f,.646f,.105f,.665f,.042f, 100,215,232,255);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void drawStandby(Matrix4f matrix) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(b, matrix, 0.075f, 0.080f, 1.085f, 0.615f, 0.046f, 13, 22, 30, 255);
        quad(b, matrix, 0.47f, 0.31f, 0.69f, 0.385f, 0.050f, 56, 142, 157, 255);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void drawScreen(Matrix4f matrix, MCEFBrowser browser) {
        // Screen glass is opaque. Do not let transparent world layers (clouds,
        // particles) blend through the browser texture.
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        // The tablet screen is an opaque object. Writing depth prevents cloud
        // and particle layers from appearing through the display.
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // Larger Z is toward the camera. Keep the live texture in front of the shell.
        float x0=.075f, y0=.080f, x1=1.085f, y1=.615f, z=.046f;
        b.addVertex(matrix,x0,y0,z).setUv(0,1).setColor(255,255,255,255);
        b.addVertex(matrix,x1,y0,z).setUv(1,1).setColor(255,255,255,255);
        b.addVertex(matrix,x1,y1,z).setUv(1,0).setColor(255,255,255,255);
        b.addVertex(matrix,x0,y1,z).setUv(0,0).setColor(255,255,255,255);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.depthMask(true);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0,float y0,float x1,float y1,float z,
                             int r,int g,int bl,int a) {
        b.addVertex(m,x0,y0,z).setColor(r,g,bl,a);
        b.addVertex(m,x1,y0,z).setColor(r,g,bl,a);
        b.addVertex(m,x1,y1,z).setColor(r,g,bl,a);
        b.addVertex(m,x0,y1,z).setColor(r,g,bl,a);
    }

    private static void sideX(BufferBuilder b, Matrix4f m, float x, float y0, float y1,
                              float z0, float z1, int r,int g,int bl,int a) {
        b.addVertex(m,x,y0,z0).setColor(r,g,bl,a);
        b.addVertex(m,x,y0,z1).setColor(r,g,bl,a);
        b.addVertex(m,x,y1,z1).setColor(r,g,bl,a);
        b.addVertex(m,x,y1,z0).setColor(r,g,bl,a);
    }

    private static void sideY(BufferBuilder b, Matrix4f m, float y, float x0, float x1,
                              float z0, float z1, int r,int g,int bl,int a) {
        b.addVertex(m,x0,y,z0).setColor(r,g,bl,a);
        b.addVertex(m,x1,y,z0).setColor(r,g,bl,a);
        b.addVertex(m,x1,y,z1).setColor(r,g,bl,a);
        b.addVertex(m,x0,y,z1).setColor(r,g,bl,a);
    }
}
