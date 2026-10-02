package net.muxigame.terminal.client.compat.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/** Marker for the postApply compatibility transform; contains no runtime shutdown hook. */
@Mixin(value=Minecraft.class,priority=1)
public abstract class MinecraftMcefShutdownGuardMixin {}
