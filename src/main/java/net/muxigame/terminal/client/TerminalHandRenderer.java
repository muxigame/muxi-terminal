package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

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
        try {
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

            drawFrame(stack.last(), buffers, light);
            MCEFBrowser browser = TerminalBrowserSession.current();
            if (browser != null && browser.getRenderer().getTextureID() != 0) drawScreen(stack.last(), buffers);
            else drawStandby(stack.last(), buffers);

        } finally {
            stack.popPose();
        }
        TerminalBrowserSession.finishContentFrame();
    }

    private void renderArm(PoseStack stack, MultiBufferSource buffers, int light, float side,
                           float equip, float sway, float bob, boolean twoHanded) {
        if (mc.player == null || mc.player.isInvisible()) return;
        stack.pushPose();
        try {
            // The map transform pitches a downward-facing map. A front-facing tablet
            // needs the arm to reach up from below. Keep the palm at the same pad
            // edge while correcting the shoulder-to-palm direction, not the pad.
            stack.translate(side * (twoHanded ? .03f : .17f),
                .10f - equip * .55f + bob * .03f, -.67f - sway * .05f);
            stack.mulPose(XP.rotationDegrees(-5f));
            stack.mulPose(YP.rotationDegrees(90f));
            stack.mulPose(YP.rotationDegrees(92f));
            stack.mulPose(XP.rotationDegrees(45f));
            stack.mulPose(ZP.rotationDegrees(side * -41f));
            stack.translate(side * .30f, -1.10f, .45f);
            PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
            if (side > 0) renderer.renderRightHand(stack, buffers, light, mc.player);
            else renderer.renderLeftHand(stack, buffers, light, mc.player);
        } finally {
            stack.popPose();
        }
    }

    private static void drawFrame(PoseStack.Pose matrix, MultiBufferSource buffers, int light) {
        VertexConsumer b = buffers.getBuffer(RenderType.entityCutoutNoCull(TerminalRenderTypes.WHITE));
        float x0=0f,y0=0f,x1=1.16f,y1=.70f,front=.030f,back=-.045f;
        quad(b, matrix, x0,y0,x1,y1,back, 29,33,37,255, light);
        sideX(b,matrix,x0,y0,y1,front,back, 42,47,51,255, light);
        sideX(b,matrix,x1,y0,y1,back,front, 14,17,20,255, light);
        sideY(b,matrix,y0,x0,x1,back,front, 16,19,22,255, light);
        sideY(b,matrix,y1,x0,x1,front,back, 47,52,55,255, light);
        quad(b, matrix, x0,y0,x1,y1,front, 31,36,40,255, light);
        quad(b, matrix, .035f,.035f,1.125f,.665f,.034f, 70,76,78,255, light);
        quad(b, matrix, .065f,.070f,1.095f,.625f,.038f, 5,8,12,255, light);
        quad(b, matrix, .075f,.646f,.105f,.665f,.042f, 100,215,232,255, light);
    }

    private static void drawStandby(PoseStack.Pose matrix, MultiBufferSource buffers) {
        VertexConsumer b = buffers.getBuffer(TerminalRenderTypes.SCREEN);
        quad(b, matrix, .075f, .080f, 1.085f, .615f, .046f, 13, 22, 30, 255, LightTexture.FULL_BRIGHT);
        quad(b, matrix, .47f, .31f, .69f, .385f, .050f, 56, 142, 157, 255, LightTexture.FULL_BRIGHT);
    }

    private static void drawScreen(PoseStack.Pose matrix, MultiBufferSource buffers) {
        VertexConsumer b = buffers.getBuffer(TerminalRenderTypes.SCREEN);
        float x0=.075f, y0=.080f, x1=1.085f, y1=.615f, z=.046f;
        screenQuad(b,matrix,x0,y0,x1,y1,z,255);
        var content=TerminalBrowserSession.content();
        if(content==null || !TerminalBrowserSession.contentVisible() || content.getRenderer().getTextureID()==0)return;
        var motion=TerminalBrowserSession.contentMotion();
        float childTop=y1-(y1-y0)*TerminalBrowserSession.contentToolbarRatio();
        float w=(x1-x0)*motion.scale(),h=(childTop-y0)*motion.scale();
        float left=x0+(x1-x0-w)/2,bottom=y0+(childTop-y0-h)/2;
        // The full opaque shell beneath this layer continues to occlude the world.
        b=buffers.getBuffer(motion.animating()?TerminalRenderTypes.CONTENT_MOTION:TerminalRenderTypes.CONTENT);
        screenQuad(b,matrix,left,bottom,left+w,bottom+h,z+.002f,Math.round(motion.alpha()*255));
    }

    private static void screenQuad(VertexConsumer b,PoseStack.Pose matrix,float x0,float y0,float x1,float y1,float z,int alpha){
        vertex(b,matrix,x0,y0,z,0,1,255,255,255,alpha,LightTexture.FULL_BRIGHT,0,0,1);
        vertex(b,matrix,x1,y0,z,1,1,255,255,255,alpha,LightTexture.FULL_BRIGHT,0,0,1);
        vertex(b,matrix,x1,y1,z,1,0,255,255,255,alpha,LightTexture.FULL_BRIGHT,0,0,1);
        vertex(b,matrix,x0,y1,z,0,0,255,255,255,alpha,LightTexture.FULL_BRIGHT,0,0,1);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose m, float x,float y,float z,float u,float v,
                               int r,int g,int bl,int a,int light,float nx,float ny,float nz) {
        b.addVertex(m,x,y,z).setColor(r,g,bl,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(m,nx,ny,nz);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose m, float x0,float y0,float x1,float y1,float z,
                             int r,int g,int bl,int a,int light) {
        vertex(b,m,x0,y0,z,0,0,r,g,bl,a,light,0,0,1);
        vertex(b,m,x1,y0,z,0,0,r,g,bl,a,light,0,0,1);
        vertex(b,m,x1,y1,z,0,0,r,g,bl,a,light,0,0,1);
        vertex(b,m,x0,y1,z,0,0,r,g,bl,a,light,0,0,1);
    }

    private static void sideX(VertexConsumer b, PoseStack.Pose m, float x,float y0,float y1,
                              float z0,float z1,int r,int g,int bl,int a,int light) {
        float nx=z0>z1?-1:1;
        vertex(b,m,x,y0,z0,0,0,r,g,bl,a,light,nx,0,0);
        vertex(b,m,x,y0,z1,0,0,r,g,bl,a,light,nx,0,0);
        vertex(b,m,x,y1,z1,0,0,r,g,bl,a,light,nx,0,0);
        vertex(b,m,x,y1,z0,0,0,r,g,bl,a,light,nx,0,0);
    }

    private static void sideY(VertexConsumer b, PoseStack.Pose m, float y,float x0,float x1,
                              float z0,float z1,int r,int g,int bl,int a,int light) {
        float ny=z0>z1?1:-1;
        vertex(b,m,x0,y,z0,0,0,r,g,bl,a,light,0,ny,0);
        vertex(b,m,x1,y,z0,0,0,r,g,bl,a,light,0,ny,0);
        vertex(b,m,x1,y,z1,0,0,r,g,bl,a,light,0,ny,0);
        vertex(b,m,x0,y,z1,0,0,r,g,bl,a,light,0,ny,0);
    }
}
