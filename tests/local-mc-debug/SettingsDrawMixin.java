package net.muxigame.localdebug.mixin;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.Font;import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;import org.spongepowered.asm.mixin.injection.Inject;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GuiGraphics.class) public abstract class SettingsDrawMixin {
 @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",at=@At("HEAD"))
 void string(Font f,String s,float x,float y,int c,boolean shadow,CallbackInfoReturnable<Integer> ci){net.muxigame.localdebug.SettingsProbe.draw((GuiGraphics)(Object)this,f,s,x,y);}
 @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I",at=@At("HEAD"))
 void sequence(Font f,FormattedCharSequence s,float x,float y,int c,boolean shadow,CallbackInfoReturnable<Integer> ci){var b=new StringBuilder();s.accept((index,style,cp)->{b.appendCodePoint(cp);return true;});net.muxigame.localdebug.SettingsProbe.draw((GuiGraphics)(Object)this,f,b.toString(),x,y);}
}
