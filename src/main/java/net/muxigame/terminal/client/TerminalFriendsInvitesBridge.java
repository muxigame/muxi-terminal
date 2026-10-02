package net.muxigame.terminal.client;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;

/** Only SDK packet submission; no server keys, room policy classes or renderer actor identity. */
final class TerminalFriendsInvitesBridge {
    private TerminalFriendsInvitesBridge(){}
    static JsonObject snapshot(){
        var connection=Minecraft.getInstance().getConnection();if(connection==null)throw new IllegalArgumentException("游戏连接不可用");var profile=connection.getLocalGameProfile();String self=TerminalFriendsPolicy.uid(profile.getName());if(!profile.getId().equals(TerminalFriendsPolicy.playerUuid(self)))throw new IllegalArgumentException("当前游戏身份不匹配");
        try{var result=TerminalFriendsInvitesPolicy.project(TerminalGameActionGate.raw().toString(),self);var verified=new JsonArray();for(var item:result.getAsJsonArray("onlinePlayers")){var row=item.getAsJsonObject();var info=connection.getPlayerInfo(java.util.UUID.fromString(row.get("uuid").getAsString()));if(info!=null && info.getProfile().getId().toString().equals(row.get("uuid").getAsString()) && info.getProfile().getName().equals(row.get("gameName").getAsString()))verified.add(row);}result.add("onlinePlayers",verified);return result;}
        catch(ReflectiveOperationException|LinkageError unavailable){var result=new JsonObject();result.addProperty("version",1);result.addProperty("selfUid",self);result.addProperty("enabled",false);result.addProperty("authenticated",false);result.add("rooms",new JsonArray());result.add("onlinePlayers",new JsonArray());result.add("invitations",new JsonArray());return result;}
    }
    static boolean verifiedPeer(String uid){try{var data=snapshot();if(!TerminalFriendsInvitesPolicy.yes(data,"enabled") || !TerminalFriendsInvitesPolicy.yes(data,"authenticated"))return false;for(var row:data.getAsJsonArray("onlinePlayers"))if(row.getAsJsonObject().get("uid").getAsString().equals(uid))return true;}catch(RuntimeException ignored){}return false;}
    static boolean dispatch(CefBrowser browser,CefFrame frame,String request,CefQueryCallback callback){
        if(!request.startsWith("friends.invites."))return false;if(!TerminalGameActionGate.authorized(browser,frame)){callback.failure(403,"临时邀请仅限当前已拥有的小游戏页面");return true;}
        try{
            if(request.equals("friends.invites.snapshot")){TerminalGameActionGate.invoke("request",new Class<?>[0]);callback.success(snapshot().toString());}
            else if(request.startsWith("friends.invites.action:")){var action=TerminalFriendsInvitesPolicy.action(request.substring(23),snapshot());callback.success(TerminalGameActionGate.submit(browser,frame,action.game(),action.action(),action.value()));}
            else callback.failure(404,"未知临时邀请操作");
        }catch(java.lang.reflect.InvocationTargetException rejected){callback.failure(409,"临时邀请未被服务器接收，请刷新核实");}
        catch(ReflectiveOperationException|LinkageError unavailable){callback.failure(503,"客户端 / 服务器邀请版本不匹配");}
        catch(RuntimeException invalid){callback.failure(400,"邀请无效或上次操作仍待确认，请刷新核实");}
        return true;
    }
}
