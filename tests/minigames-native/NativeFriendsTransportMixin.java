package net.muxigame.terminal.qa.mixin;
import java.net.URI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** QA-only exact website origin rewrite; preserve native exchange, cookies and headers. */
@Mixin(targets="net.muxigame.terminal.client.TerminalFriendsTransport",remap=false)
public abstract class NativeFriendsTransportMixin {
 @ModifyArg(method={"exchangeRequest","request"},at=@At(value="INVOKE",target="Ljava/net/http/HttpRequest;newBuilder(Ljava/net/URI;)Ljava/net/http/HttpRequest$Builder;"),index=0)
 private static URI qaWebsite(URI original){
  String site=System.getProperty("muxi.sso.siteURL");
  if(site==null || !site.matches("https://localhost:[0-9]{1,5}") || !"https".equals(original.getScheme()) || !"mc.muxigame.com".equals(original.getHost()) || original.getPort()!=-1 || original.getUserInfo()!=null || original.getQuery()!=null || original.getFragment()!=null
    || !(original.getPath().equals("/api/v1/auth/terminal/exchange") || original.getPath().equals("/api/v1/player/social") || original.getPath().startsWith("/api/v1/player/social/")))throw new IllegalStateException("Unexpected isolated friends transport");
  return URI.create(site).resolve(original.getRawPath());
 }
}
