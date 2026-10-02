package net.muxigame.terminal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.cinemamod.mcef.MCEFBrowser;
import java.util.function.Supplier;

/** Separate lit shell geometry from the opaque, full-bright browser panel. */
final class TerminalRenderTypes extends RenderType {
    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("muxi_terminal", "textures/item/terminal_white.png");
    static final RenderType SCREEN = panel("muxi_terminal_screen",TerminalBrowserSession::current,false);
    static final RenderType CONTENT = panel("muxi_terminal_content",TerminalBrowserSession::content,false);
    static final RenderType CONTENT_MOTION = panel("muxi_terminal_content_motion",TerminalBrowserSession::content,true);

    private static RenderType panel(String name,Supplier<MCEFBrowser> view,boolean motion){
        return create(name, DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
            // Use the normal opaque entity pass with a full-bright lightmap
            // only on the screen vertices. Shader packs may make eyes additive.
            .setShaderState(motion?RENDERTYPE_ENTITY_TRANSLUCENT_SHADER:RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
            .setTextureState(new EmptyTextureStateShard(() -> {
                var browser = view.get();
                int texture = browser == null ? 0 : browser.getRenderer().getTextureID();
                RenderSystem.setShaderTexture(0, texture != 0 ? texture
                    : Minecraft.getInstance().getTextureManager().getTexture(WHITE).getId());
            }, () -> {}))
            .setTransparencyState(motion?TRANSLUCENT_TRANSPARENCY:NO_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_DEPTH_WRITE)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .createCompositeState(false));
    }

    private TerminalRenderTypes() {
        super("muxi_terminal_screen", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, false, false, () -> {}, () -> {});
    }
}
