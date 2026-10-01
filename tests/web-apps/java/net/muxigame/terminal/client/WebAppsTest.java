package net.muxigame.terminal.client;

import java.nio.file.*;
import java.util.*;

public final class WebAppsTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[1]);TerminalWebApps store=new TerminalWebApps(file);
        if(args[0].equals("seed")){
            check(store.list().isEmpty(),"empty store");
            var app=store.save("","个人文档","https://example.com/docs?q=1#one");
            check(store.list().size()==1,"save");
            var changed=store.save(app.id(),"改名","http://localhost:8080/path");
            check(changed.name().equals("改名"),"editable name");
            store.save("","","https://www.example.org/");
            System.out.println("seed: save, edit and default hostname passed");return;
        }
        if(args[0].equals("reload")){
            var rows=store.list();check(rows.size()==2,"restart persistence");
            check(rows.getFirst().name().equals("改名") && rows.getFirst().url().equals("http://localhost:8080/path"),"restart exact fields");
            check(rows.getLast().name().equals("www.example.org"),"hostname fallback");
            store.delete(rows.getFirst().id());check(new TerminalWebApps(file).list().size()==1,"delete persistence");
            store.delete(rows.getLast().id());check(new TerminalWebApps(file).list().isEmpty(),"delete all");
            System.out.println("reload: separate JVM restart and delete persistence passed");return;
        }
        List<String> invalid=List.of("","example.com","javascript:alert(1)","data:text/html,hi","file:///C:/secret","mod://muxi_terminal/terminal/index.html","ftp://example.com","https://user:password@example.com","https://example.com:70000/","https://example.com:0/","https:///bad","https://good.com\\@evil.com/","https://good.com/\nX","https://good.com/%zz");
        for(String url:invalid){check(!TerminalWebPolicy.web(url),"rejected "+url);try{store.save("","x",url);throw new AssertionError("accepted "+url);}catch(IllegalArgumentException expected){}}
        check(TerminalWebPolicy.web(" HTTP://localhost:8080/ "),"HTTP allowed");
        check(TerminalWebPolicy.web("https://example.com/?next=javascript%3Afoo"),"scheme of actual URL");
        for(String url:List.of("http://mc.muxigame.com","https://mc.muxigame.com.evil.com","https://mc.muxigame.com@evil.com","https://mc.muxigame.com:8443","https://evil.com/?origin=https://mc.muxigame.com"))check(!TerminalWebPolicy.account(url),"account boundary "+url);
        check(TerminalWebPolicy.account("https://MC.MUXIGAME.COM/account.html"),"exact account domain");
        check(TerminalWebPolicy.localDocument(TerminalBrowserSessionUrl.HOME+"#/tasks"),"local hash");
        for(String url:List.of("mod://muxi_terminal.evil/terminal/index.html","mod://muxi_terminal/other.html","mod://muxi_terminal/terminal/index.html?trusted=true","mod://muxi_terminal@evil/terminal/index.html","mod://muxi_terminal/terminal/%69ndex.html"))check(!TerminalWebPolicy.localDocument(url),"local boundary "+url);
        Files.createDirectories(file.getParent());Files.writeString(file,"broken-json");
        try{store.save("","x","https://example.com");throw new AssertionError("corrupt overwritten");}catch(java.io.IOException expected){}
        check(Files.readString(file).equals("broken-json"),"corruption preserved");
        System.out.println("policy: 14 invalid URLs, account/local spoof boundaries and corruption preservation passed");
    }
    private static final class TerminalBrowserSessionUrl {static final String HOME="mod://muxi_terminal/terminal/index.html";}
}
