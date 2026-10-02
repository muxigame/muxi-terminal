package net.muxigame.terminal.client;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public class PhotoStoreTest {
    static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    static void rejects(RunnableWithException work) throws Exception {try{work.run();throw new AssertionError("Expected rejection");}catch(java.io.IOException | IllegalArgumentException expected){}}
    interface RunnableWithException {void run() throws Exception;}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);TerminalPhotoStore store=new TerminalPhotoStore(root.resolve("photos"));
        check(store.list().isEmpty(),"empty first install");
        byte[] png={(byte)137,80,78,71,13,10,26,10,1,2,3};
        rejects(()->store.save(new byte[8]));
        Set<String> ids=ConcurrentHashMap.newKeySet();
        try(var pool=Executors.newFixedThreadPool(4)){
            List<Future<?>> jobs=new ArrayList<>();
            for(int i=0;i<80;i++)jobs.add(pool.submit(()->{try{check(ids.add(store.save(png)),"name collision");}catch(Exception error){throw new RuntimeException(error);}}));
            for(var job:jobs)job.get();
        }
        check(ids.size()==80,"rapid shutters unique");check(store.list().size()==80,"list all");
        for(String id:ids){check(TerminalPhotoStore.validId(id),"opaque identifier");check(Arrays.equals(png,store.read(id)),"stored content");}
        var reloaded=new TerminalPhotoStore(root.resolve("photos"));check(reloaded.list().size()==80,"restart persistence");
        for(String id:List.of("../secret.png","C:\\secret.png","file:///secret","muxi-../../secret.png","","a.png"))rejects(()->store.read(id));
        Files.write(root.resolve("photos/unrelated.png"),png);check(store.list().size()==80,"unowned files excluded");
        String corrupt=ids.iterator().next();Files.write(root.resolve("photos").resolve(corrupt),new byte[8]);rejects(()->store.read(corrupt));
        boolean links=false;
        try {
            Path outside=root.resolve("outside");Files.createDirectories(outside);
            String linked="muxi-20261001-000000-000-00000000-0000-0000-0000-000000000000.png";
            Files.write(outside.resolve("source.png"),png);Files.createSymbolicLink(root.resolve("photos").resolve(linked),outside.resolve("source.png").toAbsolutePath());
            rejects(()->store.read(linked));check(store.list().stream().noneMatch(p->p.id().equals(linked)),"linked file excluded");
            Path directory=root.resolve("linked-root");Files.createSymbolicLink(directory,outside.toAbsolutePath());
            rejects(()->new TerminalPhotoStore(directory).save(png));links=true;
        }catch(java.nio.file.FileSystemException | UnsupportedOperationException | SecurityException unavailable){}
        System.out.println("{\"success\":true,\"concurrentSaves\":80,\"checks\":12,\"symlinkTests\":"+links+"}");
    }
}
