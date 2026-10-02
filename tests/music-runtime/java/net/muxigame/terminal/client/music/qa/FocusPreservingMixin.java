package net.muxigame.terminal.client.music.qa;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** QA-only: visible render window, without taking another owner's keyboard focus. */
@Mixin(value=Window.class,remap=false)
public abstract class FocusPreservingMixin {
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V",shift=At.Shift.AFTER))
    private void music$noFocus(CallbackInfo ci){GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);}
}
