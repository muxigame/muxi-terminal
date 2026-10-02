package net.muxigame.terminal.client.camera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.muxigame.terminal.client.camera.NativeScreenshotResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Native F2/camera pipeline keeps its events and filename rules, with explicit image ownership. */
@Mixin(value=Screenshot.class,remap=false)
public abstract class NativeScreenshotLifecycleMixin {
    @WrapMethod(method="_grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V")
    private static void muxi$ownedCapture(File game,String name,RenderTarget target,Consumer<Component> callback,Operation<Void> original){
        var previous=NativeScreenshotResources.current();var lease=NativeScreenshotResources.begin();
        try{original.call(game,name,target,callback);}
        finally{
            NativeScreenshotResources.end(lease,previous);
        }
    }
    @WrapOperation(method="_grab",at=@At(value="INVOKE",target="Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private static NativeImage muxi$ownImage(RenderTarget target,Operation<NativeImage> original){
        NativeImage image=original.call(target);var lease=NativeScreenshotResources.current();if(lease!=null)lease.image=image;return image;
    }
    @WrapOperation(method="_grab",at=@At(value="INVOKE",target="Ljava/util/concurrent/ExecutorService;execute(Ljava/lang/Runnable;)V"))
    private static void muxi$handoff(ExecutorService executor,Runnable action,Operation<Void> original){
        var lease=NativeScreenshotResources.current();NativeImage image=lease==null?null:lease.image;
        original.call(executor,(Runnable)()->{try{action.run();}finally{if(image!=null)image.close();}});
        if(lease!=null)lease.handedOff=true;
    }
    @WrapOperation(method="takeScreenshot",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;downloadTexture(IZ)V"))
    private static void muxi$readback(NativeImage image,int level,boolean opaque,Operation<Void> original){
        try{original.call(image,level,opaque);}catch(RuntimeException | Error failed){image.close();throw failed;}
    }
    @WrapOperation(method="takeScreenshot",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;flipY()V"))
    private static void muxi$flip(NativeImage image,Operation<Void> original){
        try{original.call(image);}catch(RuntimeException | Error failed){image.close();throw failed;}
    }
}
