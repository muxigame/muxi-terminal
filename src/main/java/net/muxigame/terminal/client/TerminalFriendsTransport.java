package net.muxigame.terminal.client;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.cef.network.*;

/** Native-only host-only website session. Never mint or expose a broader game/OAuth credential. */
final class TerminalFriendsTransport {
    static final int MAX_BODY=4*1024*1024;
    record Response(int status,String body){}
    static final class Failure extends IllegalStateException {final int code;Failure(int code,String message){super(message);this.code=code;}}
    @FunctionalInterface interface CookieSource {String session() throws Exception;}
    @FunctionalInterface interface Sender {Response send(HttpRequest request) throws Exception;}
    private static HttpClient http;
    private static synchronized HttpClient http(){if(http==null)http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();return http;}
    private final CookieSource cookies;private final Sender sender;
    TerminalFriendsTransport(){this(TerminalFriendsTransport::session,TerminalFriendsTransport::send);}
    TerminalFriendsTransport(CookieSource cookies,Sender sender){this.cookies=cookies;this.sender=sender;}
    static boolean validCookie(CefCookie cookie){return cookie!=null && "bmc_session".equals(cookie.name) && "mc.muxigame.com".equals(cookie.domain) && "/".equals(cookie.path) && cookie.secure && cookie.httponly && (!cookie.hasExpires || cookie.expires!=null && cookie.expires.getTime()>System.currentTimeMillis()) && validValue(cookie.value);}
    private static boolean validValue(String value){return value!=null && value.matches("[A-Za-z0-9._~-]{16,2048}");}
    private static String session() throws Exception {
        var result=new CompletableFuture<String>();var manager=CefCookieManager.getGlobalManager();
        if(manager==null || !manager.visitUrlCookies(TerminalFriendsPolicy.API,true,(cookie,count,total,delete)->{
            if(validCookie(cookie))result.complete(cookie.value);
            if(count+1>=total)result.completeExceptionally(new IllegalStateException("请先打开玩家中心登录当前账号，再返回好友"));return true;
        }))throw new IllegalStateException("请先打开玩家中心登录当前账号，再返回好友");
        try{return result.get(2,TimeUnit.SECONDS);}catch(Exception ignored){throw new IllegalStateException("请先打开玩家中心登录当前账号，再返回好友");}
    }
    private static Response send(HttpRequest request) throws Exception {
        var future=http().sendAsync(request,info->new BoundedBody());
        try{var response=future.get(5,TimeUnit.SECONDS);return new Response(response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));}
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
    String cookie() throws Exception {String value=cookies.session();if(!validValue(value))throw new IllegalStateException("请先登录玩家中心");return value;}
    String read(String cookie) throws Exception{return request("GET",TerminalFriendsPolicy.API,cookie,null);}
    String write(String cookie,TerminalFriendsPolicy.Action action) throws Exception{return request(action.method(),action.url(),cookie,action.request());}
    private String request(String method,String url,String cookie,String key) throws Exception {
        if(!validValue(cookie) || !url.startsWith(TerminalFriendsPolicy.API) || !URI.create(url).getHost().equals("mc.muxigame.com"))throw new IllegalArgumentException("无效好友目标");
        var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("Cookie","bmc_session="+cookie).header("Origin",TerminalFriendsPolicy.ORIGIN).header("Accept","application/json");
        if(key==null)builder.GET();else builder.header("Content-Type","application/json").header("Idempotency-Key",key).method(method,HttpRequest.BodyPublishers.ofString("{}"));
        var response=sender.send(builder.build());
        if(response.status()!=200)throw new Failure(response.status()>=400 && response.status()<=599?response.status():502,switch(response.status()){
            case 401->"登录已失效，请打开玩家中心登录";case 403->"当前关系或账号不允许此操作";case 404->"玩家或申请不可用，请刷新列表";case 400->"操作无效、请求冲突或已达上限，请刷新";case 503->"好友服务尚未启用";default->"好友服务未响应，结果未确认，请刷新核实";});
        return response.body();
    }
}
