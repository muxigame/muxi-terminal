package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import java.util.Base64;

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
        TerminalPassportNavigation.install();
    }

    private static final class Handler extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                               boolean persistent, CefQueryCallback callback) {
            Minecraft mc = Minecraft.getInstance();
            String frameUrl=frame==null?"":frame.getURL();
            if(frameUrl==null || !frameUrl.startsWith("mod://muxi_terminal/")) {
                callback.failure(403,"Native bridge is available only to local Muxi Terminal apps");
                return true;
            }
            if(request.equals("passport.open")) {
                var current=TerminalBrowserSession.current();
                if(current==null || browser.getIdentifier()!=current.getIdentifier() || !frame.isMain()) {
                    callback.failure(403,"Passport is available only in the terminal main frame");return true;
                }
                mc.execute(TerminalPassportNavigation::open);callback.success("{\"ok\":true}");return true;
            }
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
            if(request.startsWith("terminal.external:")) {
                boolean ok=TerminalBrowserSession.openExternal(request.substring("terminal.external:".length()));
                if(ok) callback.success("{\"ok\":true}"); else callback.failure(403,"External URL is not allowed");
                return true;
            }
            if(request.startsWith("resource.data:")) {
                resourceData(mc,request.substring("resource.data:".length()),callback);
                return true;
            }
            if(request.equals("tasks.snapshot")) {
                mc.execute(() -> callback.success(invokeTaskString("snapshotJson")));
                return true;
            }
            if(request.equals("tasks.request")) {
                mc.execute(()->invokeTaskVoid("request",null)); callback.success("{\"ok\":true}"); return true;
            }
            if(request.startsWith("tasks.claim:")) {
                String id=request.substring("tasks.claim:".length());
                mc.execute(()->invokeTaskVoid("claim",id)); callback.success("{\"ok\":true}"); return true;
            }
            if(request.startsWith("tasks.reroll:")) {
                String id=request.substring("tasks.reroll:".length());
                mc.execute(()->invokeTaskVoid("reroll",id)); callback.success("{\"ok\":true}"); return true;
            }
            if(request.startsWith("tasks.track:")) {
                String id=request.substring("tasks.track:".length());
                mc.execute(()->invokeTaskVoid("toggleTracked",id)); callback.success("{\"ok\":true}"); return true;
            }
            if(request.equals("challenge.open")) {
                mc.execute(Handler::openChallenge); callback.success("{\"ok\":true}"); return true;
            }
            if(request.startsWith("manual.open:")) {
                String manual=request.substring("manual.open:".length());
                if(manual.startsWith("patchouli:")) {
                    String value=manual.substring("patchouli:".length());
                    ResourceLocation id=ResourceLocation.tryParse(value);
                    if(id==null){callback.failure(400,"Invalid Patchouli book id");return true;}
                    if(!ModList.get().isLoaded("patchouli")){callback.failure(404,"Patchouli is not installed");return true;}
                    mc.execute(()->openPatchouliBook(id)); callback.success("{\"ok\":true}"); return true;
                }
                if("starcatcher".equals(manual)) {
                    mc.execute(Handler::openStarcatcherGuide); callback.success("{\"ok\":true}"); return true;
                }
                if("alexsmobs".equals(manual)) {
                    mc.execute(Handler::openAlexDictionary); callback.success("{\"ok\":true}"); return true;
                }
                if("iceandfire".equals(manual)) {
                    mc.execute(Handler::openIceAndFireBestiary); callback.success("{\"ok\":true}"); return true;
                }
                if("create".equals(manual)) {
                    mc.execute(Handler::openCreatePonderIndex); callback.success("{\"ok\":true}"); return true;
                }
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

        private static void resourceData(Minecraft mc,String value,CefQueryCallback callback) {
            ResourceLocation id=ResourceLocation.tryParse(value);
            if(id==null){callback.failure(400,"Invalid resource id");return;}
            try {
                var resource=mc.getResourceManager().getResource(id).orElseThrow();
                byte[] data;
                try(var stream=resource.open()){data=stream.readAllBytes();}
                String mime=id.getPath().endsWith(".png")?"image/png":"application/octet-stream";
                callback.success("data:"+mime+";base64,"+Base64.getEncoder().encodeToString(data));
            } catch(Exception error){callback.failure(404,"Resource not found: "+id);}
        }

        private static String invokeTaskString(String method) {
            try {
                Class<?> api=Class.forName("net.muxigame.core.client.tasks.TerminalTasksApi");
                return (String)api.getMethod(method).invoke(null);
            } catch(ReflectiveOperationException | LinkageError error) {
                return "{\"supported\":false,\"loading\":false,\"rows\":[],\"error\":\"Game Core task API unavailable\"}";
            }
        }

        private static void invokeTaskVoid(String method,String value) {
            try {
                Class<?> api=Class.forName("net.muxigame.core.client.tasks.TerminalTasksApi");
                if(value==null) api.getMethod(method).invoke(null);
                else api.getMethod(method,String.class).invoke(null,value);
            } catch(ReflectiveOperationException | LinkageError ignored) {}
        }

        private static void openStarcatcherGuide() {
            try {
                Class<?> screen=Class.forName("com.wdiscute.starcatcher.guide.FishingGuideScreen");
                Class<?> signed=Class.forName("com.wdiscute.starcatcher.data.SignedGuide");
                screen.getMethod("open",BlockPos.class,signed).invoke(null,BlockPos.ZERO,null);
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Starcatcher fishing guide",error);
            }
        }

        private static void openAlexDictionary() {
            try {
                ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("alexsmobs","animal_dictionary")));
                Class<?> alex=Class.forName("com.github.alexthe666.alexsmobs.AlexsMobs");
                Object proxy=alex.getField("PROXY").get(null);
                proxy.getClass().getMethod("openBookGUI",ItemStack.class).invoke(proxy,stack);
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Alex's Mobs animal dictionary",error);
            }
        }

        private static void openIceAndFireBestiary() {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null)return;
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath("iceandfire","bestiary");
            var item=BuiltInRegistries.ITEM.get(id);
            ItemStack book=ItemStack.EMPTY;
            for(ItemStack candidate:mc.player.getInventory().items) if(candidate.is(item)){book=candidate.copy();break;}
            if(book.isEmpty() && mc.player.getOffhandItem().is(item))book=mc.player.getOffhandItem().copy();
            if(book.isEmpty()){
                mc.player.displayClientMessage(Component.literal("需要先获得《怪物图鉴》，终端不会跳过图鉴页解锁。"),false);
                return;
            }
            try {
                Class<?> menuClass=Class.forName("com.iafenvoy.iceandfire.screen.menu.BestiaryMenu");
                Object menu=menuClass.getConstructor(int.class,net.minecraft.world.entity.player.Inventory.class)
                    .newInstance(0,mc.player.getInventory());
                var field=menuClass.getDeclaredField("bookStack");field.setAccessible(true);field.set(menu,book);
                Class<?> screenClass=Class.forName("com.iafenvoy.iceandfire.screen.gui.bestiary.BestiaryScreen");
                Object screen=screenClass.getConstructor(menuClass,net.minecraft.world.entity.player.Inventory.class,Component.class)
                    .newInstance(menu,mc.player.getInventory(),Component.translatable("bestiary_gui"));
                mc.setScreen((net.minecraft.client.gui.screens.Screen)screen);
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Ice and Fire bestiary",error);
            }
        }

        private static void openCreatePonderIndex() {
            try {
                Class<?> type=Class.forName("net.createmod.ponder.foundation.ui.PonderIndexScreen");
                Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen)type.getConstructor().newInstance());
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Create Ponder index",error);
            }
        }

        private static void openChallenge() {
            try {
                Class<?> challenge=Class.forName("net.muxigame.core.client.challenge.ChallengeClient");
                challenge.getMethod("open").invoke(null);
            } catch(ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("Game Core challenge UI is unavailable",error);
            }
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
