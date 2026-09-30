package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

/** A deliberately tiny capability bridge. Web apps do not get arbitrary Java access. */
public final class TerminalNativeBridge {
    private static CefMessageRouter router;

    private TerminalNativeBridge() {}

    public static void installWhenReady() {
        if (MCEF.isInitialized()) install();
        else MCEF.scheduleForInit(success -> { if (success) install(); });
    }

    private static synchronized void install() {
        if (router != null) return;
        router = CefMessageRouter.create(new Handler());
        MCEF.getClient().getHandle().addMessageRouter(router);
    }

    private static final class Handler extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                               boolean persistent, CefQueryCallback callback) {
            Minecraft mc = Minecraft.getInstance();
            if (request.equals("terminal.home")) {
                mc.execute(TerminalBrowserSession::home);
                callback.success("{\"ok\":true}");
                return true;
            }
            if (request.equals("terminal.close")) {
                mc.execute(() -> mc.setScreen(null));
                callback.success("{\"ok\":true}");
                return true;
            }
            if (request.startsWith("patchouli.open:")) {
                String value = request.substring("patchouli.open:".length());
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id == null) {
                    callback.failure(400, "Invalid book id");
                    return true;
                }
                if (!ModList.get().isLoaded("patchouli")) {
                    callback.failure(404, "Patchouli is not installed");
                    return true;
                }
                mc.execute(() -> openPatchouliBook(id));
                callback.success("{\"ok\":true}");
                return true;
            }
            callback.failure(404, "Unknown terminal command");
            return true;
        }

        private static void openPatchouliBook(ResourceLocation id) {
            try {
                Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
                Object api = apiClass.getMethod("get").invoke(null);
                api.getClass().getMethod("openBookGUI", ResourceLocation.class).invoke(api, id);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Patchouli API is present but could not open book " + id, error);
            }
        }
    }
}
