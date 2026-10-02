package net.muxigame.terminal.qa.mixin;

import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;

/** Runs the QA isolation plugin after the original relay/network mixins. */
@Mixin(value=ServerConnectionListener.class, priority=100)
public abstract class PrivateRelayIsolationMixin {}
