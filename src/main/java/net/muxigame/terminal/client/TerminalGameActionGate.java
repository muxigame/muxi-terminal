package net.muxigame.terminal.client;
import com.google.gson.*;
import org.cef.browser.*;
import net.minecraft.client.Minecraft;
import java.lang.ref.WeakReference;

/** One packet mutation per actual connection, shared by original game UI and social invitations. */
final class TerminalGameActionGate {
    private record Pending(WeakReference<Object> connection,String request){}
    private static Pending pending;
    private TerminalGameActionGate(){}
    static Object invoke(String method,Class<?>[] types,Object... args)throws ReflectiveOperationException{return Class.forName("net.muxigame.minigames.client.TerminalGamesApi").getMethod(method,types).invoke(null,args);}
    static JsonObject raw()throws ReflectiveOperationException{var parsed=JsonParser.parseString(invoke("snapshotJson",new Class<?>[0]).toString());if(!parsed.isJsonObject())throw new IllegalArgumentException("小游戏协议不匹配");return parsed.getAsJsonObject();}
    static boolean authorized(CefBrowser browser,CefFrame frame){return TerminalFriendsBridge.authorized(browser,frame,true);}
    static synchronized String submit(CefBrowser browser,CefFrame frame,String game,String action,String value)throws ReflectiveOperationException{
        if(!authorized(browser,frame))throw new IllegalArgumentException("操作仅限当前小游戏页面");var connection=Minecraft.getInstance().getConnection();
        if(pending!=null && pending.connection().get()!=connection)pending=null;
        if(pending!=null){var op=raw().get("operation");if(op!=null && op.isJsonObject()){var row=op.getAsJsonObject();if(row.has("request")&&row.has("status")&&row.get("request").getAsString().equals(pending.request())&&java.util.Set.of("completed","failed").contains(row.get("status").getAsString()))pending=null;}if(pending!=null)throw new IllegalStateException("上次小游戏操作仍待服务器确认，请刷新核实");}
        String sent=invoke("submit",new Class<?>[]{String.class,String.class,String.class},game,action,value).toString();var ack=JsonParser.parseString(sent).getAsJsonObject();
        if(!TerminalFriendsInvitesPolicy.yes(ack,"ok"))throw new IllegalArgumentException("小游戏提交未确认");String request=TerminalFriendsInvitesPolicy.uuid(TerminalFriendsInvitesPolicy.text(ack,"request"));pending=new Pending(new WeakReference<>(connection),request);return ack.toString();
    }
}
