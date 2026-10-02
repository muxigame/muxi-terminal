package net.muxigame.terminal.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import java.util.LinkedHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.cef.callback.CefQueryCallback;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import java.util.Base64;
import java.nio.ByteBuffer;
import java.util.Map;

/** Public trusted-terminal icon capability. No persistent textures, images or native cache. */
@EventBusSubscriber(modid="muxi_terminal",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TerminalIcons {
    private static final Gson JSON=new Gson();
    private static volatile long revision;
    private static int liveTargets;
    private TerminalIcons(){}

    @SubscribeEvent public static void registerReload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)manager->revision++);
    }
    public static boolean dispatch(String request,CefQueryCallback callback){
        if(!request.startsWith("icons."))return false;
        try{
            if(request.equals("icons.state")){
                callback.success(JSON.toJson(Map.of("revision",revision,"liveTargets",liveTargets,"nativeCacheEntries",0)));return true;
            }
            if(!request.startsWith("icons.get:"))throw new IllegalArgumentException("Unknown icon command");
            var data=JsonParser.parseString(request.substring(10)).getAsJsonObject();
            String kind=data.get("kind").getAsString(),value=data.get("id").getAsString();
            ResourceLocation id=ResourceLocation.tryParse(value);
            if(id==null || value.length()>256)throw new IllegalArgumentException("Invalid icon id");
            Minecraft mc=Minecraft.getInstance();
            String png;
            if(kind.equals("item")){
                if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Unknown item: "+id);
                var stack=new ItemStack(BuiltInRegistries.ITEM.get(id));
                if(stack.isEmpty())throw new IllegalArgumentException("Empty item icon");
                png=renderItem(mc,stack);
            }else if(kind.equals("resource")){
                if(!id.getPath().startsWith("textures/") || !id.getPath().endsWith(".png") || id.getPath().contains(".."))
                    throw new IllegalArgumentException("Only PNG textures are supported");
                try(var stream=mc.getResourceManager().getResource(id).orElseThrow().open()){
                    byte[] bytes=stream.readNBytes(262145);
                    if(bytes.length>262144)throw new IllegalArgumentException("Icon resource exceeds 256 KiB");
                    var header=ByteBuffer.wrap(bytes);
                    if(bytes.length<33 || header.getLong(0)!=0x89504e470d0a1a0aL || header.getInt(8)!=13 || header.getInt(12)!=0x49484452)
                        throw new IllegalArgumentException("Invalid PNG icon resource");
                    int width=header.getInt(16),height=header.getInt(20);
                    if(width<1 || height<1 || width>256 || height>256)throw new IllegalArgumentException("Resource icons are limited to 256x256; use the item model for large textures");
                    png="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes);
                }
            }else throw new IllegalArgumentException("Use semantic icons in the shared web component");
            callback.success(JSON.toJson(Map.of("revision",revision,"src",png,"kind",kind,"id",id.toString())));
        }catch(Exception error){Throwable cause=error;while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();callback.failure(400,cause.getMessage()==null?"Icon unavailable":cause.getMessage());}
        return true;
    }

    static String renderItem(Minecraft mc,ItemStack stack) throws Exception{
        RenderSystem.assertOnRenderThread();
        // Preserve the caller's target/projection. Each request frees its framebuffer and pixel buffer.
        int framebuffer=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int[] scissorBox=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] bindings=new int[12];for(int i=0;i<bindings.length;i++){RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);bindings[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}RenderSystem.activeTexture(activeTexture);
        var shader=RenderSystem.getShader();float[] color=RenderSystem.getShaderColor().clone();
        int[] textures=new int[12];for(int i=0;i<textures.length;i++)textures[i]=RenderSystem.getShaderTexture(i);
        TextureTarget target=null;
        RenderSystem.backupProjectionMatrix();RenderSystem.getModelViewStack().pushMatrix();
        var glintBuffers=new LinkedHashMap<RenderType,ByteBufferBuilder>();
        for(var type:new RenderType[]{RenderType.glint(),RenderType.glintTranslucent(),RenderType.entityGlint(),RenderType.entityGlintDirect(),RenderType.armorEntityGlint()})glintBuffers.put(type,new ByteBufferBuilder(4096));
        try(var vertices=new ByteBufferBuilder(262144)){
            target=new TextureTarget(64,64,true,Minecraft.ON_OSX);liveTargets++;
            target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.disableScissor();RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,64,64,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1,1,1,1);
            var buffers=MultiBufferSource.immediateWithBuffers(glintBuffers,vertices);
            var gui=new GuiGraphics(mc,buffers);gui.pose().translate(0,0,-11000);gui.pose().scale(4,4,1);
            gui.renderItem(stack,0,0);gui.flush();
            try(var pixels=new NativeImage(64,64,false)){
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(target.getColorTextureId());pixels.downloadTexture(0,false);pixels.flipY();
                return "data:image/png;base64,"+Base64.getEncoder().encodeToString(pixels.asByteArray());
            }
        }finally{
            for(var buffer:glintBuffers.values())buffer.close();
            if(target!=null){target.destroyBuffers();liveTargets--;}
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            RenderSystem.restoreProjectionMatrix();RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.depthMask(depthWrite);RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);if(!scissor)RenderSystem.disableScissor();
            RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            for(int i=0;i<textures.length;i++){RenderSystem.setShaderTexture(i,textures[i]);RenderSystem.activeTexture(GL13.GL_TEXTURE0+i);RenderSystem.bindTexture(bindings[i]);}
            RenderSystem.activeTexture(activeTexture);
        }
    }
}
