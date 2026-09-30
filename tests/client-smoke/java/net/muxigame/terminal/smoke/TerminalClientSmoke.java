package net.muxigame.terminal.smoke;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.muxigame.core.feature.tasks.TaskNetwork;
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
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

@Mod(value="muxi_terminal_smoke", dist=Dist.CLIENT)
public final class TerminalClientSmoke {
    private int ticks;
    private int stageFrames;
    private int stage;
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
                installTaskFixture();
                opened=true;
                TerminalClient.openHome();
                return;
            }
            if (!(mc.screen instanceof TerminalScreen)) return;
            var browser=TerminalBrowserSession.current();
            if (browser==null || browser.getRenderer().getTextureID()==0) return;
            if (++stageFrames < (stage==2?100:60)) return;

            if (GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_VISIBLE)!=GLFW.GLFW_FALSE)
                throw new AssertionError("Smoke window must remain invisible");
            try (var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(Path.of(stage==0?"terminal-home.png":stage==1?"terminal-guide.png":"terminal-tasks.png"));
            }
            if(stage==0){stage=1;stageFrames=0;TerminalClient.openApp("guide");return;}
            if(stage==1){stage=2;stageFrames=0;TerminalClient.openApp("tasks");return;}
            String url=browser.getURL();
            if(!url.contains("#/tasks")) throw new AssertionError("Task app route did not open: "+url);
            String result="{\"success\":true,\"url\":"+new com.google.gson.Gson().toJson(url)
                +",\"textureId\":"+browser.getRenderer().getTextureID()+",\"taskFrames\":"+stageFrames
                +",\"windowVisible\":false,\"screenshots\":[\"terminal-home.png\",\"terminal-guide.png\",\"terminal-tasks.png\"]}";
            Files.writeString(Path.of("client-smoke-result.json"),result);
            finished=true;mc.stop();
        } catch(Throwable error) {
            failure(error);
        }
    }

    private static void installTaskFixture() throws Exception {
        List<TaskNetwork.Row> rows=List.of(
            new TaskNetwork.Row("terminal_iron","矿洞寻铁","挖掘铁矿石，验证终端任务列表、奖励和追踪状态。",9,16,"",false,List.of(new ItemStack(Items.EMERALD,4),new ItemStack(Items.IRON_INGOT,8)),1),
            new TaskNetwork.Row("terminal_patrol","清理弓手","击败足够数量的骷髅后领取奖励。",18,18,"",false,List.of(new ItemStack(Items.ARROW,32)),2),
            new TaskNetwork.Row("terminal_hard","高难狩猎","完成一次高难度狩猎目标。",0,1,"",false,List.of(new ItemStack(Items.DIAMOND,2)),3,true));
        TaskNetwork.Snapshot snapshot=new TaskNetwork.Snapshot("2026-10-01",Instant.now().getEpochSecond()+14400,Instant.now().getEpochSecond(),rows,false,"终端任务夹具",1);
        Class<?> client=Class.forName("net.muxigame.core.client.tasks.DailyTasksClient");
        Method receive=client.getDeclaredMethod("receive",TaskNetwork.Snapshot.class);receive.setAccessible(true);receive.invoke(null,snapshot);
        Class<?> api=Class.forName("net.muxigame.core.client.tasks.TerminalTasksApi");
        api.getMethod("toggleTracked",String.class).invoke(null,"terminal_iron");
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

