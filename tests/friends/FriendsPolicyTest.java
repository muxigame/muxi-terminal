package net.muxigame.terminal.client;
import com.google.gson.*;
import java.util.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public final class FriendsPolicyTest {
    static int checks;static void check(boolean b,String text){checks++;if(!b)throw new AssertionError(text);}static void deny(Runnable r){checks++;try{r.run();throw new AssertionError("allowed invalid policy");}catch(IllegalArgumentException expected){}}
    static JsonObject peer(String uid,boolean online){var row=new JsonObject();row.addProperty("uid",uid);row.addProperty("displayName","<img src=x onerror=boom>");row.addProperty("gameName",uid);row.addProperty("uuid",TerminalFriendsPolicy.playerUuid(uid).toString());row.addProperty("online",online);return row;}
    static JsonObject data(){var d=new JsonObject();d.addProperty("version",1);d.addProperty("selfUid","100001");d.addProperty("authenticated",true);d.addProperty("identityMode","platform-uid");d.addProperty("presenceAvailable",true);for(String key:List.of("friends","incoming","outgoing","blocked","onlinePlayers"))d.add(key,new JsonArray());d.getAsJsonArray("friends").add(peer("100002",true));d.getAsJsonArray("onlinePlayers").add(peer("100002",true));return d;}
    public static void main(String[] args) throws Exception {
        for(String uid:List.of("1234","0100001","100001 ","@a","100001\n/msg @a","99999999999999999"))deny(()->TerminalFriendsPolicy.uid(uid));
        check(TerminalFriendsPolicy.uid("9007199254740993").equals("9007199254740993"),"large decimal UID intact");
        var raw=data();var d=TerminalFriendsPolicy.snapshot(raw.toString(),"100001");check(d.getAsJsonArray("friends").size()==1,"valid projection");check(d.getAsJsonArray("friends").get(0).getAsJsonObject().get("displayName").getAsString().startsWith("<img"),"display only text preserved");
        deny(()->TerminalFriendsPolicy.snapshot(raw.toString(),"100009"));
        try{TerminalFriendsPolicy.snapshot("\"synthetic_upstream_secret\"","100001");throw new AssertionError("primitive accepted");}catch(IllegalArgumentException error){check(!error.getMessage().contains("synthetic_upstream_secret"),"malformed upstream body not exposed to renderer");}
        for(String key:List.of("version","authenticated","identityMode")){var bad=raw.deepCopy();bad.addProperty(key,"wrong");deny(()->TerminalFriendsPolicy.snapshot(bad.toString(),"100001"));}
        var bad=raw.deepCopy();bad.getAsJsonArray("friends").add(peer("100002",true));var duplicate=bad;deny(()->TerminalFriendsPolicy.snapshot(duplicate.toString(),"100001"));
        bad=raw.deepCopy();bad.getAsJsonArray("friends").get(0).getAsJsonObject().addProperty("uuid",UUID.randomUUID().toString());var wrongUuid=bad;deny(()->TerminalFriendsPolicy.snapshot(wrongUuid.toString(),"100001"));
        bad=raw.deepCopy();bad.getAsJsonArray("friends").get(0).getAsJsonObject().addProperty("gameName","@a");var wrongName=bad;deny(()->TerminalFriendsPolicy.snapshot(wrongName.toString(),"100001"));
        String key="11111111-1111-4111-8111-111111111111";
        for(String op:List.of("request","accept","cancel","remove","block","unblock")){var action=TerminalFriendsPolicy.action("{\"op\":\""+op+"\",\"uid\":\"100002\",\"request\":\""+key+"\"}");check(action.url().startsWith(TerminalFriendsPolicy.API+"/"),"fixed API mutation path");check(action.request().equals(key),"key intact");}
        deny(()->TerminalFriendsPolicy.action("{\"op\":\"request\",\"uid\":\"100002\",\"request\":\""+key+"\",\"actor\":\"100009\"}"));deny(()->new TerminalFriendsPolicy.Action("execute","100002",key));deny(()->new TerminalFriendsPolicy.Action("block","../outside",key));
        var action=new TerminalFriendsPolicy.Action("request","100002",key);String receipt="{\"version\":1,\"request\":\""+key+"\",\"ok\":true,\"status\":\"ok\",\"changed\":false}";check(!TerminalFriendsPolicy.receipt(receipt,action).get("changed").getAsBoolean(),"valid idempotent unchanged receipt");deny(()->TerminalFriendsPolicy.receipt(receipt.replace(key,UUID.randomUUID().toString()),action));
        check(TerminalFriendsPolicy.chatPrefill(d,"100002",TerminalFriendsPolicy.playerUuid("100002"),"100002",true,true).equals("/msg 100002 "),"registered msg preferred, prefill only");check(TerminalFriendsPolicy.chatPrefill(d,"100002",TerminalFriendsPolicy.playerUuid("100002"),"100002",false,true).equals("/tell 100002 "),"registered tell fallback");
        deny(()->TerminalFriendsPolicy.chatPrefill(d,"100002",TerminalFriendsPolicy.playerUuid("100002"),"@a",true,true));deny(()->TerminalFriendsPolicy.chatPrefill(d,"100002",UUID.randomUUID(),"100002",true,true));deny(()->TerminalFriendsPolicy.chatPrefill(d,"100002",TerminalFriendsPolicy.playerUuid("100002"),"100002",false,false));deny(()->TerminalFriendsPolicy.chatPrefill(d,"100003",TerminalFriendsPolicy.playerUuid("100003"),"100003",true,true));
        var calls=new ArrayList<HttpRequest>();String cookie="A".repeat(54);
        var transport=new TerminalFriendsTransport(()->cookie,request->{calls.add(request);return new TerminalFriendsTransport.Response(200,request.method().equals("GET")?raw.toString():receipt);});
        String value=transport.cookie();transport.read(value);transport.write(value,action);
        check(calls.size()==2 && calls.get(0).method().equals("GET") && calls.get(1).method().equals("PUT"),"actual HTTP methods with fake sender");check(calls.stream().allMatch(r->r.uri().getScheme().equals("https") && r.uri().getHost().equals("mc.muxigame.com")),"credential destination fixed HTTPS host");check(calls.get(1).headers().firstValue("Origin").orElseThrow().equals(TerminalFriendsPolicy.ORIGIN),"same-origin native write");check(calls.get(1).headers().firstValue("Idempotency-Key").orElseThrow().equals(key),"native idempotency header");check(calls.get(1).bodyPublisher().orElseThrow().contentLength()==2,"empty JSON only");
        check(calls.stream().allMatch(r->r.headers().firstValue("Authorization").orElseThrow().equals("Bearer "+cookie)),"unchanged original account access on every operation");
        check(calls.stream().allMatch(r->r.headers().firstValue("Cookie").isEmpty()),"no independent website session");
        check(calls.stream().allMatch(r->!r.uri().toString().contains(cookie)),"no access in URL");
        var fail=new TerminalFriendsTransport(()->cookie,r->new TerminalFriendsTransport.Response(303,"https://outside.example"));
        try{fail.read(cookie);throw new AssertionError("followed redirect");}catch(TerminalFriendsTransport.Failure error){check(error.code==502,"redirect refused without exposing location");}
        var nativePayload=new JsonObject();nativePayload.addProperty("accessToken",cookie);nativePayload.addProperty("uid",100001);nativePayload.addProperty("gameSession",key);
        check(TerminalFriendsTransport.originalAccess(nativePayload.toString()).equals(cookie),"same original access from native binding");
        for(String invalid:List.of("", "{}",nativePayload.toString().replace(cookie,"expired"),nativePayload.toString().replace("100001","9999"),nativePayload.toString().replace(key,"bad-session"),nativePayload.toString().replace("accessToken","ticket"))){
            try{TerminalFriendsTransport.originalAccess(invalid);throw new AssertionError("invalid access context accepted");}catch(TerminalFriendsTransport.Failure error){check(error.code==401 && !error.getMessage().contains(cookie),"invalid native context fails closed without credential exposure");}
        }
        var forged=nativePayload.deepCopy();forged.addProperty("role","admin");
        try{TerminalFriendsTransport.originalAccess(forged.toString());throw new AssertionError("role claim accepted");}catch(TerminalFriendsTransport.Failure error){check(error.code==401,"native identity cannot add a role");}
        class Subscription implements Flow.Subscription {boolean cancelled;public void request(long n){}public void cancel(){cancelled=true;}}
        var body=new TerminalFriendsTransport.BoundedBody();var subscription=new Subscription();body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(new byte[TerminalFriendsTransport.MAX_BODY+1])));check(subscription.cancelled && body.getBody().toCompletableFuture().isCompletedExceptionally(),"oversized body cancelled before buffer allocation");
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"scope\":\"policy and original Bearer HTTP transport, fake sender only; no production requests\"}");
    }
}
