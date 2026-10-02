package net.muxigame.terminal.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict projection of the authenticated business API; display names never identify a player. */
public final class TerminalFriendsPolicy {
    public static final String ORIGIN="https://mc.muxigame.com";
    public static final String API=ORIGIN+"/api/v1/player/social";
    private TerminalFriendsPolicy(){}
    public record Action(String op,String uid,String request){
        public Action {uid=TerminalFriendsPolicy.uid(uid);if(!Set.of("request","accept","cancel","remove","block","unblock").contains(op) || !UUID.fromString(request).toString().equals(request))throw new IllegalArgumentException("无效好友操作");}
        public String method(){return switch(op){case "request","block"->"PUT";case "accept"->"POST";default->"DELETE";};}
        public String url(){return API+switch(op){case "request","cancel"->"/requests/"+uid;case "accept"->"/requests/"+uid+"/accept";case "remove"->"/friends/"+uid;default->"/blocks/"+uid;};}
    }
    public static String uid(String value){if(value==null || !value.matches("[1-9][0-9]{4,15}"))throw new IllegalArgumentException("请输入 5–16 位平台 UID");return value;}
    public static UUID playerUuid(String uid){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+uid(uid)).getBytes(StandardCharsets.UTF_8));}
    private static String string(JsonObject object,String key){var value=object.get(key);if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("好友协议不匹配");return value.getAsString();}
    private static boolean bool(JsonObject object,String key){var value=object.get(key);if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("好友协议不匹配");return value.getAsBoolean();}
    private static void version(JsonObject object){var value=object.get("version");if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().equals("1"))throw new IllegalArgumentException("好友服务版本不匹配，请更新后重试");}
    private static JsonObject object(String body){var value=JsonParser.parseString(body);if(!value.isJsonObject())throw new IllegalArgumentException("好友服务响应不匹配");return value.getAsJsonObject();}
    public static Action action(String json){
        var value=JsonParser.parseString(json);if(!value.isJsonObject())throw new IllegalArgumentException("无效好友操作");var data=value.getAsJsonObject();
        if(!data.keySet().equals(Set.of("op","uid","request")))throw new IllegalArgumentException("无效好友操作");
        String op=string(data,"op"),target=uid(string(data,"uid")),request=string(data,"request");
        if(!Set.of("request","accept","cancel","remove","block","unblock").contains(op) || !UUID.fromString(request).toString().equals(request))throw new IllegalArgumentException("无效好友操作");
        return new Action(op,target,request);
    }
    public static JsonObject snapshot(String body,String expectedUid){
        var data=object(body);version(data);
        String self=uid(string(data,"selfUid"));if(!self.equals(uid(expectedUid)) || !bool(data,"authenticated") || !string(data,"identityMode").equals("platform-uid"))throw new IllegalArgumentException("玩家中心账号与当前游戏账号不一致，请先切换账号");
        boolean presence=bool(data,"presenceAvailable");var result=new JsonObject();result.addProperty("version",1);result.addProperty("selfUid",self);result.addProperty("authenticated",true);result.addProperty("identityMode","platform-uid");result.addProperty("presenceAvailable",presence);
        for(String key:List.of("friends","incoming","outgoing","blocked","onlinePlayers")){
            var source=data.get(key);int limit=switch(key){case "friends"->500;case "blocked"->1000;case "onlinePlayers"->1024;default->100;};
            if(source==null || !source.isJsonArray() || source.getAsJsonArray().size()>limit)throw new IllegalArgumentException("好友列表超过协议限制");
            var rows=new JsonArray();var seen=new HashSet<String>();
            for(var item:source.getAsJsonArray()){
                if(!item.isJsonObject())throw new IllegalArgumentException("好友玩家信息不匹配");var peer=item.getAsJsonObject();String id=uid(string(peer,"uid")),name=string(peer,"displayName"),gameName=string(peer,"gameName"),uuid=string(peer,"uuid");boolean online=bool(peer,"online");
                if(id.equals(self) || !seen.add(id) || name.codePointCount(0,name.length())>80 || !gameName.equals(id) || !uuid.equals(playerUuid(id).toString()) || (!presence && online) || (key.equals("onlinePlayers") && !online))throw new IllegalArgumentException("好友玩家身份不匹配");
                var row=new JsonObject();row.addProperty("uid",id);row.addProperty("displayName",name);row.addProperty("gameName",gameName);row.addProperty("uuid",uuid);row.addProperty("online",online);rows.add(row);
            }result.add(key,rows);
        }
        if(result.getAsJsonArray("incoming").size()+result.getAsJsonArray("outgoing").size()>100)throw new IllegalArgumentException("好友申请超过协议限制");
        var limits=new JsonObject();limits.addProperty("friends",500);limits.addProperty("pending",100);limits.addProperty("blocked",1000);limits.addProperty("online",1024);result.add("limits",limits);return result;
    }
    public static JsonObject receipt(String body,Action action){
        var data=object(body);version(data);
        if(!string(data,"request").equals(action.request()) || !string(data,"status").equals("ok") || !bool(data,"ok"))throw new IllegalArgumentException("好友操作回执不匹配，请刷新核实");
        var result=new JsonObject();result.addProperty("version",1);result.addProperty("request",action.request());result.addProperty("status","ok");result.addProperty("ok",true);result.addProperty("changed",bool(data,"changed"));return result;
    }
    static JsonObject peer(JsonObject snapshot,String list,String uid){for(var item:snapshot.getAsJsonArray(list)){var row=item.getAsJsonObject();if(row.get("uid").getAsString().equals(uid))return row;}return null;}
    public static String chatPrefill(JsonObject snapshot,String uid,UUID actualUuid,String actualName,boolean msg,boolean tell){
        uid=uid(uid);var friend=peer(snapshot,"friends",uid);var online=peer(snapshot,"onlinePlayers",uid);
        if(!snapshot.get("presenceAvailable").getAsBoolean() || friend==null || online==null || peer(snapshot,"blocked",uid)!=null || !friend.get("online").getAsBoolean() || !actualName.equals(uid) || !actualUuid.equals(playerUuid(uid)) || !online.get("gameName").getAsString().equals(actualName) || !online.get("uuid").getAsString().equals(actualUuid.toString()) || (!msg && !tell))throw new IllegalArgumentException("好友当前不在线或原生私信命令不可用，请刷新");
        return (msg?"/msg ":"/tell ")+actualName+" ";
    }
}
