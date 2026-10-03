package net.muxigame.terminal.client;

import java.net.URI;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Pass the original native account access token unchanged; never mint a website session. */
final class TerminalFriendsTransport {
    static final int MAX_BODY=4*1024*1024;
    record Response(int status,String body,Map<String,List<String>> headers){
        Response(int status,String body){this(status,body,Map.of());}
        List<String> header(String name){return headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).flatMap(e->e.getValue().stream()).toList();}
    }
    static final class Failure extends IllegalStateException {final int code;Failure(int code,String message){super(message);this.code=code;}}
    @FunctionalInterface interface AccessSource {String session() throws Exception;}
    @FunctionalInterface interface Sender {Response send(HttpRequest request) throws Exception;}
    private static HttpClient http;
    private static long lastPassport;
    private static synchronized long reservePassportDelay(){long now=System.nanoTime(),at=lastPassport==0?now:Math.max(now,lastPassport+2_100_000_000L);lastPassport=at;return at-now;}
    private static synchronized HttpClient http(){if(http==null)http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();return http;}
    private final AccessSource tokens;private final Sender sender;private final boolean nativeSource;
    TerminalFriendsTransport(){this(TerminalFriendsTransport::session,TerminalFriendsTransport::send,true);}
    TerminalFriendsTransport(AccessSource tokens,Sender sender){this(tokens,sender,false);}
    private TerminalFriendsTransport(AccessSource tokens,Sender sender,boolean nativeSource){this.tokens=tokens;this.sender=sender;this.nativeSource=nativeSource;}
    private static boolean validValue(String value){return value!=null && value.matches("[A-Za-z0-9_-]{54}");}
    private static String session() throws Exception {return originalAccess(passport());}
    static String originalAccess(String payload){
        try{
            if(payload==null || payload.length()>1024)throw new IllegalArgumentException();
            var value=JsonParser.parseString(payload).getAsJsonObject();
            if(!value.keySet().equals(java.util.Set.of("accessToken","uid","gameSession"))
                || !value.get("accessToken").isJsonPrimitive() || !value.get("accessToken").getAsJsonPrimitive().isString()
                || !validValue(value.get("accessToken").getAsString()) || !value.get("uid").isJsonPrimitive()
                || !value.get("uid").getAsJsonPrimitive().isNumber() || value.get("uid").getAsLong()<10000)throw new IllegalArgumentException();
            String id=value.get("gameSession").getAsString();if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();
            return value.get("accessToken").getAsString();
        }catch(RuntimeException invalid){throw new Failure(401,"客户端账户暂不可用，请在客户端恢复登录");}
    }
    static String passport() throws Exception {
        // The original access stays in native memory and HTTPS headers only.
        var mc=Minecraft.getInstance();var connection=mc.getConnection();var player=mc.player;var screen=mc.screen;
        if(connection==null || player==null)throw new Failure(401,"客户端账户暂不可用，请在客户端恢复登录");
        // Respect the existing server's two-second passport cooldown. A recent
        // account-page opening may also use that flow, so retry one empty result.
        for(int attempt=0;attempt<2;attempt++){
            var result=new CompletableFuture<String>();
            CompletableFuture.runAsync(()->mc.execute(()->{
                if(mc.getConnection()!=connection || mc.player!=player || mc.screen!=screen){result.complete("");return;}
                try{
                    Consumer<String> callback=result::complete;
                    Class.forName("net.muxigame.core.client.TerminalPassportApi").getMethod("request",Consumer.class).invoke(null,callback);
                }catch(ReflectiveOperationException | LinkageError unavailable){result.complete("");}
            }),CompletableFuture.delayedExecutor(reservePassportDelay(),TimeUnit.NANOSECONDS));
            String payload;
            try{payload=result.get(23,TimeUnit.SECONDS);}catch(Exception unavailable){throw new Failure(401,"客户端账户暂不可用，请在客户端恢复登录");}
            if(mc.getConnection()!=connection || mc.player!=player || mc.screen!=screen)throw new Failure(409,"Terminal context changed");
            if(payload!=null && !payload.isEmpty())return payload;
        }
        throw new Failure(401,"客户端账户暂不可用，请在客户端恢复登录");
    }

    private static Response send(HttpRequest request) throws Exception {
        var future=http().sendAsync(request,info->new BoundedBody());
        try{var response=future.get(5,TimeUnit.SECONDS);return new Response(response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8),response.headers().map());}
        catch(Exception error){future.cancel(true);throw error;}
    }
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody(){return body;}
        @Override public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
        @Override public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){if(buffer.remaining()>MAX_BODY-bytes.size()){subscription.cancel();body.completeExceptionally(new IllegalStateException("好友响应过大"));return;}byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}subscription.request(1);
        }
        @Override public void onError(Throwable error){body.completeExceptionally(error);}
        @Override public void onComplete(){body.complete(bytes.toByteArray());}
    }
    String cookie() throws Exception {String value=tokens.session();if(!validValue(value))throw new IllegalStateException("客户端会话暂不可用，请返回后刷新");return value;}
    String read(String cookie) throws Exception{return request("GET",TerminalFriendsPolicy.API,cookie,null);}
    String write(String cookie,TerminalFriendsPolicy.Action action) throws Exception{return request(action.method(),action.url(),cookie,action.request());}
    private String request(String method,String url,String cookie,String key) throws Exception {
        if(nativeSource){
            // Recheck the selected launcher account immediately before each HTTP read/write.
            // A refresh may change the same account's access; a UID switch invalidates its broker.
            try{cookie=(String)Class.forName("net.muxigame.core.client.TerminalPassportApi").getMethod("freshAccessToken").invoke(null);}
            catch(ReflectiveOperationException | LinkageError unavailable){throw new Failure(401,"客户端账户已失效，请恢复登录");}
            if(!validValue(cookie))throw new Failure(401,"客户端账户已切换或注销，请恢复登录");
        }
        if(!validValue(cookie) || !url.startsWith(TerminalFriendsPolicy.API) || !URI.create(url).getHost().equals("mc.muxigame.com"))throw new IllegalArgumentException("无效好友目标");
        var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+cookie).header("Origin",TerminalFriendsPolicy.ORIGIN).header("Accept","application/json");
        if(key==null)builder.GET();else builder.header("Content-Type","application/json").header("Idempotency-Key",key).method(method,HttpRequest.BodyPublishers.ofString("{}"));
        var response=sender.send(builder.build());
        if(response.status()!=200)throw new Failure(response.status()>=400 && response.status()<=599?response.status():502,switch(response.status()){
            case 401->"会话已失效，请刷新以恢复客户端登录态";case 403->"当前关系或账号不允许此操作";case 404->"玩家或申请不可用，请刷新列表";case 400->"操作无效、请求冲突或已达上限，请刷新";case 503->"好友服务尚未启用";default->"好友服务未响应，结果未确认，请刷新核实";});
        return response.body();
    }
}
