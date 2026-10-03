package net.muxigame.terminal.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.lang.ref.WeakReference;

/** Explicit local-document capability; website credentials remain exclusively native. */
public final class TerminalFriendsBridge {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"muxi-terminal-friends");thread.setDaemon(true);return thread;});
    private static TerminalFriendsTransport TRANSPORT=new TerminalFriendsTransport();
    private static volatile Cache cache;
    private record Cache(JsonObject snapshot,WeakReference<CefBrowser> browser,WeakReference<Object> screen,WeakReference<Object> connection,long generation,long loaded){}
    private TerminalFriendsBridge(){}
    static boolean authorized(CefBrowser browser,CefFrame frame,boolean invitePeers){
        var mc=Minecraft.getInstance();String route=TerminalBrowserSession.HOME_URL+(invitePeers?"#/games":"#/friends");
        return mc.screen instanceof TerminalScreen && mc.level!=null && mc.player!=null && mc.player.isAlive() && mc.getConnection()!=null && mc.isWindowActive()
            && browser==TerminalBrowserSession.content() && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN
            && TerminalBrowserSession.trusted(browser,frame) && route.equals(browser.getURL()) && route.equals(frame.getURL());
    }
    public static boolean dispatch(CefBrowser browser,CefFrame frame,String request,CefQueryCallback callback){
        if(request.startsWith("friends.invites."))return TerminalFriendsInvitesBridge.dispatch(browser,frame,request,callback);
        if(!request.startsWith("friends."))return false;boolean peers=request.equals("friends.invite-peers");
        if(!authorized(browser,frame,peers)){callback.failure(403,"好友功能仅限当前好友页面；邀请来源仅限当前小游戏页面");return true;}
        var mc=Minecraft.getInstance();var screen=mc.screen;var player=mc.player;var level=mc.level;var connection=mc.getConnection();long generation=TerminalBrowserSession.generation();
        BooleanSupplier valid=()->authorized(browser,frame,peers) && mc.screen==screen && mc.player==player && mc.level==level && mc.getConnection()==connection && TerminalBrowserSession.generation()==generation;
        try{
            var profile=connection.getLocalGameProfile();String self=TerminalFriendsPolicy.uid(profile.getName());
            if(!profile.getId().equals(TerminalFriendsPolicy.playerUuid(self)))throw new IllegalArgumentException("当前游戏身份未与平台 UID 对应");
            if(request.startsWith("friends.message:")){
                String uid=TerminalFriendsPolicy.uid(request.substring(16));var saved=cache;
                if(saved==null || saved.browser().get()!=browser || saved.screen().get()!=screen || saved.connection().get()!=connection || saved.generation()!=generation || System.nanoTime()-saved.loaded()>30_000_000_000L || !saved.snapshot().get("selfUid").getAsString().equals(self))throw new IllegalArgumentException("请先刷新好友列表，再打开私信");
                var target=connection.getPlayerInfo(TerminalFriendsPolicy.playerUuid(uid));if(target==null)throw new IllegalArgumentException("好友已离线，请刷新");
                if(!TerminalFriendsInvitesBridge.verifiedPeer(uid))throw new IllegalArgumentException("当前连接尚未核实这个在线好友，请刷新");
                var tree=connection.getCommands().getRoot();String prefill=TerminalFriendsPolicy.chatPrefill(saved.snapshot(),uid,target.getProfile().getId(),target.getProfile().getName(),tree.getChild("msg")!=null,tree.getChild("tell")!=null);
                if(!valid.getAsBoolean()){callback.failure(409,"Terminal context changed");return true;}
                callback.success("{\"ok\":true,\"openedChat\":true,\"prefillOnly\":true}");mc.setScreen(new ChatScreen(prefill));return true;
            }
            TerminalFriendsPolicy.Action action=request.startsWith("friends.action:")?TerminalFriendsPolicy.action(request.substring(15)):null;
            if(action==null && !request.equals("friends.snapshot") && !peers){callback.failure(404,"未知好友操作");return true;}
            if(action!=null && action.uid().equals(self))throw new IllegalArgumentException("不能操作自己的好友关系");
            if(!BUSY.compareAndSet(false,true)){callback.failure(429,"好友操作处理中，请稍后刷新");return true;}
            IO.execute(()->{
                try{
                    if(!valid.getAsBoolean()){callback.failure(409,"Terminal context changed");return;}String cookie=TRANSPORT.cookie();if(!valid.getAsBoolean()){callback.failure(409,"Terminal context changed");return;}
                    // Validate the same account actor before each write; native transport rereads the current original access token.
                    var snapshot=TerminalFriendsPolicy.snapshot(TRANSPORT.read(cookie),self);if(!valid.getAsBoolean()){callback.failure(409,"Terminal context changed");return;}
                    var result=action==null?snapshot:TerminalFriendsPolicy.receipt(TRANSPORT.write(cookie,action),action);
                    if(peers){var projected=new JsonObject();for(String key:java.util.List.of("version","selfUid","presenceAvailable","friends","onlinePlayers"))projected.add(key,snapshot.get(key));result=projected;}
                    final JsonObject reply=result;
                    mc.execute(()->{
                        if(!valid.getAsBoolean()){callback.failure(409,"Terminal context changed");return;}
                        if(peers){try{reply.add("runtime",TerminalFriendsInvitesBridge.snapshot());}catch(RuntimeException unavailable){callback.failure(503,"当前小游戏邀请协议不可用，请刷新核实");return;}}
                        if(action==null && !peers){cache=new Cache(snapshot,new WeakReference<>(browser),new WeakReference<>(screen),new WeakReference<>(connection),generation,System.nanoTime());decorateMessages(snapshot,connection);}
                        else if(action!=null)cache=null;
                        callback.success(reply.toString());
                    });
                }catch(Exception error){mc.execute(()->{if(valid.getAsBoolean()){cache=null;callback.failure(error instanceof TerminalFriendsTransport.Failure failure?failure.code:400,error instanceof IllegalArgumentException || error instanceof IllegalStateException?error.getMessage():"好友请求未确认，请刷新核实");}else callback.failure(409,"Terminal context changed");});}
                finally{BUSY.set(false);}
            });
        }catch(Exception error){callback.failure(400,error instanceof IllegalArgumentException?error.getMessage():"好友功能不可用，请刷新");}
        return true;
    }
    private static void decorateMessages(JsonObject snapshot,net.minecraft.client.multiplayer.ClientPacketListener connection){
        var tree=connection.getCommands().getRoot();
        for(var item:snapshot.getAsJsonArray("friends")){var row=item.getAsJsonObject();boolean available=false;
                try{String uid=row.get("uid").getAsString();var info=connection.getPlayerInfo(TerminalFriendsPolicy.playerUuid(uid));if(info!=null && TerminalFriendsInvitesBridge.verifiedPeer(uid)){TerminalFriendsPolicy.chatPrefill(snapshot,uid,info.getProfile().getId(),info.getProfile().getName(),tree.getChild("msg")!=null,tree.getChild("tell")!=null);available=true;}}catch(IllegalArgumentException ignored){}
            row.addProperty("messageAvailable",available);
        }
    }
}
