package net.muxigame.terminal.client;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Actual native URL policy; no origins or trust claims are accepted from JavaScript. */
public final class TerminalWebPolicy {
    private static final Set<String> ACCOUNT_HOSTS=Set.of("mc.muxigame.com","account.muxigame.com");
    private TerminalWebPolicy() {}

    public static String normalize(String value) {
        if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException("请输入完整的 http:// 或 https:// 网页链接");
        String input=value.trim();
        if(input.length()>4096 || input.chars().anyMatch(c->c<32 || c==127) || input.contains("\\"))
            throw new IllegalArgumentException("网页链接包含非法字符或过长");
        try {
            URI uri=new URI(input);
            String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
            if(!Set.of("http","https").contains(scheme) || uri.isOpaque() || uri.getHost()==null
                || uri.getHost().isBlank() || uri.getRawUserInfo()!=null || uri.getPort()>65535 || uri.getPort()==0)
                throw new IllegalArgumentException("只支持不含用户名密码的 http:// 或 https:// 网页链接");
            return scheme+input.substring(input.indexOf(':'));
        } catch(java.net.URISyntaxException e){throw new IllegalArgumentException("网页链接格式无效");}
    }
    public static boolean web(String value){try{normalize(value);return true;}catch(IllegalArgumentException e){return false;}}
    public static boolean account(String value){
        try {URI uri=URI.create(normalize(value));return "https".equals(uri.getScheme())
            && ACCOUNT_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) && (uri.getPort()==-1 || uri.getPort()==443);}
        catch(IllegalArgumentException e){return false;}
    }
    public static boolean accountHost(String value){
        try{return ACCOUNT_HOSTS.contains(URI.create(normalize(value)).getHost().toLowerCase(Locale.ROOT));}
        catch(IllegalArgumentException e){return false;}
    }
    public static boolean localDocument(String value){
        try {URI u=URI.create(value);return "mod".equals(u.getScheme()) && "muxi_terminal".equals(u.getRawAuthority())
            && "/terminal/index.html".equals(u.getRawPath()) && u.getRawQuery()==null;}
        catch(IllegalArgumentException | NullPointerException e){return false;}
    }
}
