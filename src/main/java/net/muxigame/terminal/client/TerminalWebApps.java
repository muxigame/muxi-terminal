package net.muxigame.terminal.client;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Small personal bookmark store. Atomic replace; failures are visible, never overwrite broken data. */
public final class TerminalWebApps {
    public record App(String id,String name,String url) {}
    private static final Gson JSON=new Gson();
    private final Path file;
    public TerminalWebApps(Path file){this.file=file;}
    public synchronized List<App> list() throws IOException {
        if(!Files.exists(file))return new ArrayList<>();
        if(Files.size(file)>1_048_576)throw new IOException("网页应用文件过大");
        try {
            App[] rows=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),App[].class);
            if(rows==null || rows.length>100)throw new IllegalArgumentException();
            List<App> result=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(App row:rows){
                if(row==null || row.id()==null || !row.id().matches("[a-f0-9-]{36}") || !ids.add(row.id()))throw new IllegalArgumentException();
                result.add(new App(row.id(),name(row.name(),row.url()),TerminalWebPolicy.normalize(row.url())));
            }
            return result;
        }catch(RuntimeException e){throw new IOException("网页应用文件损坏，请备份后检查本地配置",e);}
    }
    public synchronized App save(String id,String title,String url) throws IOException {
        String normalized=TerminalWebPolicy.normalize(url);String label=name(title,normalized);
        List<App> rows=list();App app=new App(id==null || id.isEmpty()?UUID.randomUUID().toString():id,label,normalized);
        int index=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(app.id()))index=i;
        if(id!=null && !id.isEmpty() && index<0)throw new IllegalArgumentException("网页应用已不存在");
        if(index<0){if(rows.size()>=100)throw new IllegalArgumentException("最多保存 100 个网页应用");rows.add(app);}else rows.set(index,app);
        write(rows);return app;
    }
    public synchronized void delete(String id) throws IOException {List<App> rows=list();if(!rows.removeIf(x->x.id().equals(id)))throw new IllegalArgumentException("网页应用已不存在");write(rows);}
    public synchronized App get(String id) throws IOException {return list().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("网页应用已不存在"));}
    private static String name(String name,String url){
        String value=name==null?"":name.trim();if(value.isEmpty())value=java.net.URI.create(TerminalWebPolicy.normalize(url)).getHost();
        if(value.length()>80 || value.chars().anyMatch(c->c<32 || c==127))throw new IllegalArgumentException("名称最多 80 字且不能包含控制字符");return value;
    }
    private void write(List<App> rows) throws IOException {
        Files.createDirectories(file.getParent());Path staged=Files.createTempFile(file.getParent(),"web-apps-",".tmp");
        try {Files.writeString(staged,JSON.toJson(rows),StandardCharsets.UTF_8);
            try{Files.move(staged,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(staged,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(staged);}
    }
}
