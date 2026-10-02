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
        for(String path:List.of("../real-user-photo.png",outside.toString(),"file:///photo.png" ,"CON.png","a.png:outside")){rejects(()->store.recycle(path));rejects(()->store.restore(path));}
        check(Arrays.equals(Files.readAllBytes(outside),png),"outside photo untouched");
        Files.write(root.resolve("photos/unowned.png"),png);check(store.list().size()==2,"renamed native PNG visible");
        // Real filenames/formats, current-instance isolation and most-recent ordering.
        Path photos=root.resolve("photos");
        Files.write(photos.resolve("2026-10-02_12.34.56.png"),png);
        Files.setLastModifiedTime(photos.resolve("2026-10-02_12.34.56.png"),java.nio.file.attribute.FileTime.fromMillis(9_000_000_000_000L));
        check(store.list().getFirst().id().equals("2026-10-02_12.34.56.png"),"native F2 filename sorted newest by mtime");
        check(Arrays.equals(store.read("2026-10-02_12.34.56.png"),png),"native F2 read permitted in own instance");
        for(String ext:List.of("png","jpg","jpeg","gif","bmp")){
            var image=new java.awt.image.BufferedImage(640,480,java.awt.image.BufferedImage.TYPE_INT_RGB);
            try(var out=new java.io.ByteArrayOutputStream()){
                check(javax.imageio.ImageIO.write(image,ext.equals("jpeg")?"jpg":ext,out),"fixture codec available "+ext);
                String name="截图 重命名."+ext.toUpperCase(java.util.Locale.ROOT);Files.write(photos.resolve(name),out.toByteArray());
                check(store.list().stream().anyMatch(p->p.id().equals(name)),"format/native renamed file enumerated "+ext);
                String data=TerminalPhotoImages.data(store,name,false,240,160);
                var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data.substring(data.indexOf(',')+1))));
                check(decoded.getWidth()<=240 && decoded.getHeight()<=160,"bounded actual decoded thumbnail "+ext);decoded.flush();
            }finally{image.flush();}
        }
        Files.write(photos.resolve("invalid.png"),new byte[]{1,2,3});Files.write(photos.resolve("script.svg"),"<svg/>".getBytes());
        check(store.list().stream().noneMatch(p->p.id().equals("invalid.png") || p.id().equals("script.svg")),"invalid signature and active SVG excluded");
        var second=new TerminalPhotoStore(root.resolve("other-instance/screenshots"));check(second.list().isEmpty(),"switch instance sees only its screenshots");
        Path legacy=photos.resolve("muxi-terminal/player");Files.createDirectories(legacy);Files.write(legacy.resolve("old-camera.png"),png);
        var compatible=new TerminalPhotoStore(photos,legacy);var oldPhoto=compatible.list().stream().filter(p->p.readOnly()).findFirst().orElseThrow();
        check(oldPhoto.name().equals("old-camera.png") && Arrays.equals(compatible.read(oldPhoto.id()),png),"legacy own-player photo visible read-only without migration");
        rejects(()->compatible.recycle(oldPhoto.id()));rejects(()->compatible.restore(oldPhoto.id()));
        check(Files.exists(legacy.resolve("old-camera.png")) && !Files.exists(photos.resolve("old-camera.png")),"legacy originals untouched, no duplicate copy");
        for(int n=0;n<210;n++){Path extra=photos.resolve("sort-"+n+".png");Files.write(extra,png);Files.setLastModifiedTime(extra,java.nio.file.attribute.FileTime.fromMillis(10_000+n));}
        check(store.list().size()==200 && store.list().getFirst().id().equals("2026-10-02_12.34.56.png"),"newest 200 selected independently of enumeration order");
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
