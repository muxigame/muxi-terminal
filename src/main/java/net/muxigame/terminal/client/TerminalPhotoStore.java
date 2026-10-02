package net.muxigame.terminal.client;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Direct children of the current instance screenshot directory only. Never scans other instances. */
public final class TerminalPhotoStore {
    public static final int MAX_BYTES=64*1024*1024;
    private static final String LEGACY="~legacy~";
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final Comparator<Photo> NEWEST=Comparator.comparingLong(Photo::modified).reversed().thenComparing(Photo::id);
    private final Path root,legacy;
    public record Photo(String id,String name,long bytes,long modified,String source,boolean readOnly) {}
    public TerminalPhotoStore(Path root){this(root,null);}
    public TerminalPhotoStore(Path root,Path legacy){
        this.root=root.toAbsolutePath().normalize();this.legacy=legacy==null?null:legacy.toAbsolutePath().normalize();
        if(this.legacy!=null && !this.legacy.startsWith(this.root.resolve("muxi-terminal")))throw new IllegalArgumentException("Invalid legacy library");
    }
    public Path directory(){return root;}
    public String save(byte[] png) throws IOException {
        if(png.length>MAX_BYTES || !isPng(png))throw new IOException("Invalid or oversized photo");
        checkAncestors();Files.createDirectories(root);checkRoot();
        for(int attempt=0;attempt<8;attempt++){
            String id="muxi-"+TIME.format(Instant.now())+"-"+UUID.randomUUID()+".png";
            Path target=resolve(id);java.io.OutputStream stream;
            try{stream=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);}
            catch(FileAlreadyExistsException ignored){continue;}
            try(var out=stream){out.write(png);}catch(IOException error){Files.deleteIfExists(target);throw error;}
            return id;
        }
        throw new IOException("Could not allocate photo name");
    }
    public List<Photo> list() throws IOException {
        // Keep the newest 200 without retaining every entry or depending on directory enumeration order.
        PriorityQueue<Photo> newest=new PriorityQueue<>(201,NEWEST.reversed());
        enumerate(newest);
        if(legacy!=null){
            var previous=new TerminalPhotoStore(legacy);
            for(Photo p:previous.list())offer(newest,new Photo(LEGACY+p.id(),p.name(),p.bytes(),p.modified(),"legacy",true));
        }
        List<Photo> result=new ArrayList<>(newest);result.sort(NEWEST);return List.copyOf(result);
    }
    private void enumerate(PriorityQueue<Photo> newest) throws IOException {
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return;
        checkRoot();
        try(var entries=Files.newDirectoryStream(root)){
            for(Path p:entries){
                String name=p.getFileName().toString();if(!validName(name))continue;
                try{
                    var attrs=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    if(!attrs.isRegularFile() || attrs.size()<2 || attrs.size()>MAX_BYTES)continue;
                    try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){if(!signature(name,in.readNBytes(8)))continue;}
                    offer(newest,new Photo(name,name,attrs.size(),attrs.lastModifiedTime().toMillis(),"screenshots",false));
                }catch(IOException disappeared){/* Native F2 saves/renames concurrently; one entry cannot break the album. */}
            }
        }
    }
    private static void offer(PriorityQueue<Photo> newest,Photo p){newest.offer(p);if(newest.size()>200)newest.poll();}
    public List<Photo> recycled() throws IOException {return trash().list();}
    public byte[] read(String id) throws IOException {
        if(isLegacy(id)){if(legacy==null)throw new IOException("Legacy photo unavailable");return new TerminalPhotoStore(legacy).read(id.substring(LEGACY.length()));}
        checkRoot();Path p=ownedFile(id);
        try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){
            byte[] data=in.readNBytes(MAX_BYTES+1);
            if(data.length>MAX_BYTES || !signature(id,data))throw new IOException("Invalid or oversized photo");return data;
        }
    }
    public byte[] readRecycled(String id) throws IOException {requireWritable(id);checkRoot();return trash().read(id);}
    /** Same-directory recoverable move; never deletes or copies the original image bytes. */
    public synchronized void recycle(String id) throws IOException {
        requireWritable(id);checkRoot();Path source=ownedFile(id);TerminalPhotoStore bin=trash();
        bin.checkAncestors();Files.createDirectories(bin.root);bin.checkRoot();moveWithoutReplacement(source,bin.resolve(id));
    }
    public synchronized void restore(String id) throws IOException {
        requireWritable(id);checkRoot();TerminalPhotoStore bin=trash();bin.checkRoot();moveWithoutReplacement(bin.ownedFile(id),resolve(id));
    }
    static void requireWritable(String id){if(!validId(id) || isLegacy(id))throw new IllegalArgumentException("Legacy photos are read-only");}
    private TerminalPhotoStore trash(){return new TerminalPhotoStore(root.resolve(".recycle"));}
    private Path ownedFile(String id) throws IOException {
        Path p=resolve(id);var attrs=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile() || attrs.size()>MAX_BYTES)throw new IOException("Photo unavailable");
        try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){if(!signature(id,in.readNBytes(8)))throw new IOException("Invalid photo");}
        return p;
    }
    private static void moveWithoutReplacement(Path source,Path target) throws IOException {
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.getFileName().toString());Files.move(source,target);
    }
    private void checkRoot() throws IOException {checkAncestors();if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Photo directory unavailable");}
    private void checkAncestors() throws IOException {
        for(Path p=root;p!=null;p=p.getParent()){
            if(Files.isSymbolicLink(p) || (Files.exists(p,LinkOption.NOFOLLOW_LINKS) && !p.toRealPath().equals(p.toRealPath(LinkOption.NOFOLLOW_LINKS))))throw new IOException("Linked photo directory is not allowed");
        }
    }
    private Path resolve(String id){if(!validName(id))throw new IllegalArgumentException("Invalid photo id");return root.resolve(id);}
    static boolean isLegacy(String id){return id!=null && id.startsWith(LEGACY);}
    static boolean validId(String id){return validName(isLegacy(id)?id.substring(LEGACY.length()):id);}
    private static boolean validName(String id){
        if(id==null || id.isBlank() || id.length()>240 || id.startsWith(".") || id.startsWith(LEGACY) || id.endsWith(" "))return false;
        for(int i=0;i<id.length();i++)if(id.charAt(i)<32 || "\\/:*?\"<>|".indexOf(id.charAt(i))>=0)return false;
        String lower=id.toLowerCase(Locale.ROOT);int dot=lower.lastIndexOf('.');if(dot<1)return false;
        String stem=lower.substring(0,dot);if(stem.matches("(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))return false;
        return Set.of("png","jpg","jpeg","gif","bmp").contains(lower.substring(dot+1));
    }
    private static boolean signature(String name,byte[] b){
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".png"))return isPng(b);
        if(lower.endsWith(".jpg") || lower.endsWith(".jpeg"))return b.length>=3 && b[0]==(byte)255 && b[1]==(byte)216 && b[2]==(byte)255;
        if(lower.endsWith(".bmp"))return b.length>=2 && b[0]==66 && b[1]==77;
        return lower.endsWith(".gif") && b.length>=6 && b[0]==71 && b[1]==73 && b[2]==70 && b[3]==56 && (b[4]==55 || b[4]==57) && b[5]==97;
    }
    private static boolean isPng(byte[] b){return b.length>=8 && b[0]==(byte)137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;}
}
