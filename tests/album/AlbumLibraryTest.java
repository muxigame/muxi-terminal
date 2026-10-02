package net.muxigame.terminal.client;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
public final class AlbumLibraryTest {
    static int checks;
    static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    interface Work {void run() throws Exception;}
    static void rejects(Work work) throws Exception {checks++;try{work.run();throw new AssertionError("Expected rejection");}catch(java.io.IOException | IllegalArgumentException expected){}}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);var store=new TerminalPhotoStore(root.resolve("photos"));
        byte[] png={(byte)137,80,78,71,13,10,26,10,1,2,3};
        check(store.list().isEmpty() && store.recycled().isEmpty(),"empty new install");
        String id=store.save(png);check(store.list().size()==1,"camera save immediately in album");
        store.recycle(id);check(store.list().isEmpty() && store.recycled().size()==1,"move reflected in both lists");
        check(!Files.exists(root.resolve("photos").resolve(id)),"no duplicate source copy");
        check(Arrays.equals(store.readRecycled(id),png),"original photo bytes retained");
        rejects(()->store.read(id));
        var reloaded=new TerminalPhotoStore(root.resolve("photos"));check(reloaded.recycled().size()==1,"trash survives restart");
        reloaded.restore(id);check(Arrays.equals(store.read(id),png) && store.recycled().isEmpty(),"restore original bytes/name");
        store.recycle(id);byte[] replacement=png.clone();replacement[8]=99;
        Files.write(root.resolve("photos").resolve(id),replacement);rejects(()->store.restore(id));
        check(Arrays.equals(store.read(id),replacement) && Arrays.equals(store.readRecycled(id),png),"restore never overwrites existing photo");
        Files.delete(root.resolve("photos").resolve(id));store.restore(id);
        Files.write(root.resolve("photos/.recycle").resolve(id),replacement);rejects(()->store.recycle(id));
        check(Arrays.equals(store.read(id),png) && Arrays.equals(store.readRecycled(id),replacement),"recycle never overwrites existing trash photo");
        Path outside=root.resolve("real-user-photo.png");Files.write(outside,png);
        for(String path:List.of("../real-user-photo.png",outside.toString(),"file:///photo.png","a.png")){rejects(()->store.recycle(path));rejects(()->store.restore(path));}
        check(Arrays.equals(Files.readAllBytes(outside),png),"outside photo untouched");
        Files.write(root.resolve("photos/unowned.png"),png);check(store.list().size()==1,"unowned resource excluded");
        boolean linkChecks=false;
        try{
            String linked="muxi-20261001-000000-000-00000000-0000-0000-0000-000000000000.png";
            Files.createSymbolicLink(root.resolve("photos").resolve(linked),outside.toAbsolutePath());
            rejects(()->store.recycle(linked));check(Arrays.equals(Files.readAllBytes(outside),png),"linked outside photo untouched");
            Path linkRoot=root.resolve("linked-library");Files.createSymbolicLink(linkRoot,root.resolve("photos").toAbsolutePath());
            rejects(()->new TerminalPhotoStore(linkRoot).recycle(id));linkChecks=true;
        }catch(FileSystemException | UnsupportedOperationException | SecurityException unavailable){}
        AtomicLong now=new AtomicLong(100);var confirmation=new TerminalAlbumConfirmation(now::get);Object browser=new Object(),other=new Object();
        var ticket=confirmation.prepare(id,browser,4);rejects(()->confirmation.consume(id,browser,4));
        rejects(()->confirmation.consume(ticket.token(),other,4));rejects(()->confirmation.consume(ticket.token(),browser,5));
        check(confirmation.consume(ticket.token(),browser,4).equals(id),"owner one-use confirmation consumed");
        rejects(()->confirmation.consume(ticket.token(),browser,4));
        String expired=confirmation.prepare(id,browser,4).token();now.addAndGet(30_000);rejects(()->confirmation.consume(expired,browser,4));
        var cancelled=confirmation.prepare(id,browser,4);confirmation.cancel(browser,4);rejects(()->confirmation.consume(cancelled.token(),browser,4));
        var old=confirmation.prepare(id,browser,4);var next=confirmation.prepare(id,browser,4);rejects(()->confirmation.consume(old.token(),browser,4));check(!next.token().equals(old.token()),"old dialog superseded");
        rejects(()->confirmation.prepare("../outside",browser,4));
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"symlinkTests\":"+linkChecks+",\"photoScope\":\"temporary synthetic library only\"}");
    }
}
