package net.muxigame.terminal.client;
import com.google.gson.*;
import java.util.*;

/** Native SDK snapshot projection and full-session invitation validation. */
final class TerminalFriendsInvitesPolicy {
    static final Set<String> GAMES=Set.of("zombie-challenge","outbreak");
    static String text(JsonObject row,String key){var value=row.get(key);if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("邀请信息不匹配");return value.getAsString();}
    static boolean yes(JsonObject row,String key){var value=row.get(key);return value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();}
    static String uuid(String value){if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException("请选择完整有效的邀请 / 房间标识");return value;}
    static JsonArray rows(JsonObject row,String key,int max){var value=row.get(key);if(value==null || !value.isJsonArray() || value.getAsJsonArray().size()>max)throw new IllegalArgumentException("邀请列表不可用");return value.getAsJsonArray();}
    static JsonObject project(String raw,String self){
        var parsed=JsonParser.parseString(raw);if(!parsed.isJsonObject())throw new IllegalArgumentException("小游戏协议不匹配");var root=parsed.getAsJsonObject();var out=new JsonObject();out.addProperty("version",1);out.addProperty("selfUid",TerminalFriendsPolicy.uid(self));out.addProperty("selfUuid",TerminalFriendsPolicy.playerUuid(self).toString());out.addProperty("enabled",false);out.addProperty("authenticated",false);
        out.add("rooms",new JsonArray());out.add("onlinePlayers",new JsonArray());out.add("invitations",new JsonArray());
        var value=root.get("social");if(!yes(root,"actionSupported") || root.get("protocol")==null || !root.get("protocol").isJsonPrimitive() || !root.get("protocol").getAsString().equals("2") || value==null || !value.isJsonObject())return out;
        var social=value.getAsJsonObject();if(social.get("version")==null || !social.get("version").isJsonPrimitive() || !social.get("version").getAsString().equals("1") || !yes(social,"enabled") || !yes(social,"authenticated"))return out;
        out.addProperty("enabled",true);out.addProperty("authenticated",true);var seen=new HashSet<String>();
        for(var item:rows(social,"onlinePlayers",64)){
            if(!item.isJsonObject())throw new IllegalArgumentException("在线玩家信息不匹配");var peer=item.getAsJsonObject();String uid=TerminalFriendsPolicy.uid(text(peer,"uid"));String name=text(peer,"gameName"),id=uuid(text(peer,"uuid")),display=text(peer,"displayName");
            if(uid.equals(self) || !seen.add(uid) || !name.equals(uid) || !id.equals(TerminalFriendsPolicy.playerUuid(uid).toString()) || display.length()>1024)throw new IllegalArgumentException("在线玩家身份不匹配");
            var copy=new JsonObject();copy.addProperty("uid",uid);copy.addProperty("uuid",id);copy.addProperty("gameName",name);copy.addProperty("displayName",display);copy.addProperty("online",true);out.getAsJsonArray("onlinePlayers").add(copy);
        }
        seen.clear();for(var item:rows(social,"invitations",128)){
            if(!item.isJsonObject())throw new IllegalArgumentException("邀请记录不匹配");var ticket=item.getAsJsonObject();String id=uuid(text(ticket,"invitation")),room=uuid(text(ticket,"room")),host=uuid(text(ticket,"host")),target=uuid(text(ticket,"target")),game=text(ticket,"game"),source=text(ticket,"source"),status=text(ticket,"status");
            if(!seen.add(id) || !GAMES.contains(game) || !Set.of("online","friends").contains(source) || !Set.of("PENDING","ACCEPTED","DECLINED","CANCELLED","EXPIRED").contains(status) || (!host.equals(out.get("selfUuid").getAsString()) && !target.equals(out.get("selfUuid").getAsString())))throw new IllegalArgumentException("邀请记录不属于当前玩家");
            var expiry=ticket.get("expiresInSeconds");if(expiry==null || !expiry.isJsonPrimitive() || !expiry.getAsJsonPrimitive().isNumber() || !expiry.getAsString().matches("[0-9]{1,4}") || expiry.getAsInt()>300)throw new IllegalArgumentException("邀请有效期不匹配");
            var copy=new JsonObject();for(String key:List.of("invitation","room","game","host","target","source","status","expiresInSeconds"))copy.add(key,ticket.get(key));out.getAsJsonArray("invitations").add(copy);
        }
        for(var item:rows(root,"games",16)){
            var game=item.getAsJsonObject();String id=text(game,"id");if(!GAMES.contains(id))continue;var state=game.getAsJsonObject("state");if(state==null)continue;
            for(var roomItem:rows(state,"rooms",128)){
                var room=roomItem.getAsJsonObject();if(!yes(room,"socialManaged") || room.get("session")==null || room.get("host")==null)continue;
                String session=uuid(text(room,"session")),host=uuid(text(room,"host")),phase=text(room,"phase");
                var copy=new JsonObject();copy.addProperty("game",id);copy.addProperty("session",session);copy.addProperty("host",host);copy.addProperty("mine",yes(room,"mine"));copy.addProperty("socialManaged",true);copy.addProperty("phase",phase);
                boolean waiting=id.equals("zombie-challenge")?Set.of("LOBBY","BUILDING").contains(phase):yes(room,"lobbyWaiting")&&Set.of("PREPARING","COUNTDOWN").contains(phase);
                copy.addProperty("inviteable",waiting && yes(room,"mine") && host.equals(out.get("selfUuid").getAsString()));out.getAsJsonArray("rooms").add(copy);
            }
        }
        var operation=root.get("operation");if(operation!=null && operation.isJsonObject()){
            var op=operation.getAsJsonObject();String request=uuid(text(op,"request")),status=text(op,"status");if(!Set.of("pending","completed","failed").contains(status))throw new IllegalArgumentException("小游戏回执状态不匹配");
            var copy=new JsonObject();copy.addProperty("request",request);copy.addProperty("status",status);String notice=op.has("notice")?text(op,"notice"):"";copy.addProperty("notice",notice.length()>1024?"操作状态已更新":notice);out.add("operation",copy);
        }
        return out;
    }
    record Action(String game,String action,String value){}
    static Action action(String json,JsonObject snapshot){
        if(!yes(snapshot,"enabled") || !yes(snapshot,"authenticated"))throw new IllegalArgumentException("当前服务器未提供可用的临时邀请");
        var data=JsonParser.parseString(json).getAsJsonObject();String op=text(data,"op"),game=text(data,"game"),self=text(snapshot,"selfUuid");if(!GAMES.contains(game))throw new IllegalArgumentException("小游戏不可用");
        if(op.equals("issue")){
            if(!data.keySet().equals(Set.of("op","game","source","room","uid")))throw new IllegalArgumentException("无效邀请字段");String source=text(data,"source"),session=uuid(text(data,"room")),uid=TerminalFriendsPolicy.uid(text(data,"uid"));if(!Set.of("online","friends").contains(source))throw new IllegalArgumentException("邀请来源不可用");
            boolean owns=false;for(var item:snapshot.getAsJsonArray("rooms")){var room=item.getAsJsonObject();if(text(room,"game").equals(game)&&text(room,"session").equals(session)&&yes(room,"inviteable")&&text(room,"host").equals(self))owns=true;}if(!owns)throw new IllegalArgumentException("房间已变化或你不是等待中的房主");
            JsonObject target=null;for(var item:snapshot.getAsJsonArray("onlinePlayers")){var peer=item.getAsJsonObject();if(text(peer,"uid").equals(uid))target=peer;}if(target==null)throw new IllegalArgumentException("目标不在当前已核实的在线玩家中");
            return new Action(game,source.equals("friends")?"inviteFriends":"inviteOnline",session+"|"+text(target,"uuid"));
        }
        if(!Set.of("accept","decline","cancel").contains(op) || !data.keySet().equals(Set.of("op","game","invitation")))throw new IllegalArgumentException("无效邀请操作");String id=uuid(text(data,"invitation"));JsonObject selected=null;
        for(var item:snapshot.getAsJsonArray("invitations")){var ticket=item.getAsJsonObject();if(text(ticket,"invitation").equals(id)&&text(ticket,"game").equals(game))selected=ticket;}
        if(selected==null || !text(selected,"status").equals("PENDING") || selected.get("expiresInSeconds").getAsInt()<=0 || !(op.equals("cancel")?text(selected,"host"):text(selected,"target")).equals(self))throw new IllegalArgumentException("邀请已处理、过期或不属于当前玩家");
        return new Action(game,switch(op){case "accept"->"inviteAccept";case "decline"->"inviteDecline";default->"inviteCancel";},id);
    }
}
