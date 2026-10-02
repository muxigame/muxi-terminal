package net.muxigame.terminal.client;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Only opaque app-owned PNG identifiers cross the bridge; never arbitrary paths. */
public final class TerminalPhotoStore {
    public static final int MAX_BYTES=64*1024*1024;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final Path root;
    public record Photo(String id,long bytes,long modified) {}
    public TerminalPhotoStore(Path root){this.root=root.toAbsolutePath().normalize();}
    public String save(byte[] png) throws IOException {
        if(png.length>MAX_BYTES || !isPng(png))throw new IOException("Invalid or oversized photo");
        checkAncestors();Files.createDirectories(root);checkRoot();
        for(int attempt=0;attempt<8;attempt++){
            String id="muxi-"+TIME.format(Instant.now())+"-"+UUID.randomUUID()+".png";
            Path target=resolve(id);
            java.io.OutputStream stream;
            try{stream=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
            catch(FileAlreadyExistsException ignored){continue;}
            try(var out=stream){out.write(png);}
            catch(IOException error){Files.deleteIfExists(target);throw error;}
            return id;
        }
        throw new IOException("Could not allocate photo name");
    }
    public List<Photo> list() throws IOException {
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return List.of();
        checkRoot();
        try(var stream=Files.list(root)){
            List<Photo> photos=new ArrayList<>();
            // Bound enumeration to avoid an unbounded bridge response.
            for(Path p:stream.limit(10000).toList()){
                if(!validId(p.getFileName().toString()) || !Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))continue;
                long size=Files.size(p);if(size>MAX_BYTES)continue;
                photos.add(new Photo(p.getFileName().toString(),size,Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toMillis()));
            }
            photos.sort(Comparator.comparing(Photo::id).reversed());return photos.stream().limit(200).toList();
        }
    }
    public List<Photo> recycled() throws IOException {return trash().list();}
    public byte[] read(String id) throws IOException {
        checkRoot();Path p=resolve(id);
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>MAX_BYTES)throw new IOException("Photo unavailable");
        try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){
            byte[] data=in.readNBytes(MAX_BYTES+1);
            if(data.length>MAX_BYTES || !isPng(data))throw new IOException("Invalid or oversized photo");return data;
        }
    }
    public byte[] readRecycled(String id) throws IOException {checkRoot();return trash().read(id);}
    /** Same-library move only. No delete API and no second copy of photo bytes. */
    public synchronized void recycle(String id) throws IOException {
        checkRoot();Path source=ownedFile(id);TerminalPhotoStore bin=trash();
        bin.checkAncestors();Files.createDirectories(bin.root);bin.checkRoot();
        moveWithoutReplacement(source,bin.resolve(id));
    }
    public synchronized void restore(String id) throws IOException {
        checkRoot();TerminalPhotoStore bin=trash();bin.checkRoot();
        moveWithoutReplacement(bin.ownedFile(id),resolve(id));
    }
    private TerminalPhotoStore trash(){return new TerminalPhotoStore(root.resolve(".recycle"));}
    private Path ownedFile(String id) throws IOException {
        Path p=resolve(id);
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>MAX_BYTES)throw new IOException("Photo unavailable");
        // Validate the app-owned image signature before any mutating operation.
        try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){if(!isPng(in.readNBytes(8)))throw new IOException("Invalid photo");}
        return p;
    }
    private static void moveWithoutReplacement(Path source,Path target) throws IOException {
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.getFileName().toString());
        // Do not use ATOMIC_MOVE: its existing-target semantics are implementation-specific.
        Files.move(source,target);
    }
    private void checkRoot() throws IOException {
        checkAncestors();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Photo directory unavailable");
    }
    private void checkAncestors() throws IOException {
        // Reject links/junctions in all existing ancestors, including the final directory.
        for(Path p=root;p!=null;p=p.getParent()){
            if(Files.isSymbolicLink(p) || (Files.exists(p,LinkOption.NOFOLLOW_LINKS) &&
                !p.toRealPath().equals(p.toRealPath(LinkOption.NOFOLLOW_LINKS))))throw new IOException("Linked photo directory is not allowed");
        }
    }
    private Path resolve(String id){if(!validId(id))throw new IllegalArgumentException("Invalid photo id");return root.resolve(id);}
    static boolean validId(String id){return id!=null && id.matches("muxi-[0-9]{8}-[0-9]{6}-[0-9]{3}-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");}
    private static boolean isPng(byte[] b){return b.length>=8 && b[0]==(byte)137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;}
}
