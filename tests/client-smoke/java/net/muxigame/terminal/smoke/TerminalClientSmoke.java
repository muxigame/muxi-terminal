package net.muxigame.terminal.smoke;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.muxigame.terminal.client.TerminalBrowserSession;
import net.muxigame.terminal.client.TerminalClient;
import net.muxigame.terminal.client.TerminalScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(value="muxi_terminal_smoke", dist=Dist.CLIENT)
public final class TerminalClientSmoke {
    private int ticks;
    private int rendered;
    private boolean opened;
    private boolean finished;

    public TerminalClientSmoke() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if (++ticks > 900) throw new IllegalStateException("Timed out waiting for terminal browser");
            if (!opened) {
                if (ticks < 30 || mc.getOverlay()!=null || mc.screen==null || !MCEF.isInitialized()) return;
                opened=true;
                TerminalClient.openHome();
                return;
            }
            if (!(mc.screen instanceof TerminalScreen)) return;
            var browser=TerminalBrowserSession.current();
            if (browser==null || browser.getRenderer().getTextureID()==0) return;
            if (++rendered < 70) return;

            if (GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_VISIBLE)!=GLFW.GLFW_FALSE)
                throw new AssertionError("Smoke window must remain invisible");
            try (var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(Path.of("terminal-home.png"));
            }
            String url=browser.getURL();
            String result="{\"success\":true,\"url\":"+new com.google.gson.Gson().toJson(url)
                +",\"textureId\":"+browser.getRenderer().getTextureID()+",\"frames\":"+rendered
                +",\"windowVisible\":false}";
            Files.writeString(Path.of("client-smoke-result.json"),result);
            finished=true;
            mc.stop();
        } catch(Throwable error) {
            failure(error);
        }
    }

    private void failure(Throwable error) {
        finished=true;
        error.printStackTrace();
        try {
            Files.writeString(Path.of("client-smoke-result.json"),
                "{\"success\":false,\"error\":"+new com.google.gson.Gson().toJson(error.toString())+"}");
        } catch(Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}

