package net.muxigame.terminal.client.music;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read compressed Vorbis page metadata only; stop as soon as music is long enough. */
public final class MusicCatalogFilter {
    public static final double DEFAULT_MINIMUM_SECONDS=10;
    public interface Opener { InputStream open() throws IOException; }
    public record Metadata(double seconds,boolean exact,long encodedBytes) {
        boolean reusable(double minimum){return exact || seconds>=minimum;}
        public boolean keep(double minimum){return seconds<0 || seconds>=minimum;}
    }
    public record Result(boolean keep,boolean cacheHit,Metadata metadata){}
    private final Map<String,Metadata> cache=new ConcurrentHashMap<>();
    public Result inspect(String key,Opener source,double minimum)throws IOException{
        if(!Double.isFinite(minimum)||minimum<0||minimum>120)throw new IllegalArgumentException("Invalid music duration threshold");
        Metadata old=cache.get(key);
        if(old!=null && old.reusable(minimum))return new Result(old.keep(minimum),true,old);
        Metadata measured;
        try(InputStream input=source.open()){measured=read(input,minimum);}
        catch(InterruptedIOException cancelled){throw cancelled;}
        catch(IOException malformed){measured=new Metadata(-1,true,0);}
        // Unknown metadata remains playable. The real decoder remains authoritative.
        if(cache.size()<4096)cache.put(key,measured);
        return new Result(measured.keep(minimum),false,measured);
    }
    public void clear(){cache.clear();}
    public static boolean musicPath(String path){
        if(!path.endsWith(".ogg") || !(path.startsWith("sounds/music/") || path.contains("/music/")))return false;
        // Exclude explicit effect/ambient directories; don't blacklist musical titles.
        for(String part:path.split("/"))if(Set.of("ambient","ambience","sfx","sound_effects").contains(part))return false;
        return true;
    }
    public static Metadata read(InputStream input,double minimum)throws IOException{
        int serial=0,rate=0;boolean identified=false,ended=false;long lastGranule=0,encoded=0;
        for(int page=0;page<20000;page++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Music scan cancelled");
            byte[] header=input.readNBytes(27);
            if(header.length==0){
                if(identified && ended)return new Metadata(lastGranule/(double)rate,true,encoded);
                throw new IOException("Missing Vorbis end page");
            }
            if(header.length!=27 || header[0]!='O' || header[1]!='g' || header[2]!='g' || header[3]!='S' || header[4]!=0)
                throw new IOException("Invalid Ogg page");
            byte[] lacing=input.readNBytes(Byte.toUnsignedInt(header[26]));
            if(lacing.length!=Byte.toUnsignedInt(header[26]))throw new EOFException();
            int size=0;for(byte segment:lacing)size+=Byte.toUnsignedInt(segment);
            byte[] first=input.readNBytes(Math.min(size,30));
            if(first.length!=Math.min(size,30))throw new EOFException();
            int pageSerial=littleInt(header,14);
            if(!identified){
                if(first.length<30 || first[0]!=1 || !new String(first,1,6,java.nio.charset.StandardCharsets.US_ASCII).equals("vorbis"))
                    throw new IOException("Unsupported Ogg codec");
                rate=littleInt(first,12);serial=pageSerial;identified=true;
                if(rate<8000 || rate>192000)throw new IOException("Invalid Vorbis rate");
            }else if(pageSerial!=serial)throw new IOException("Chained Ogg metadata unsupported");
            input.skipNBytes(size-first.length);encoded+=27+lacing.length+size;
            if(encoded>64L*1024*1024)throw new IOException("Ogg metadata scan budget exceeded");
            long granule=0;for(int n=0;n<8;n++)granule|=Byte.toUnsignedLong(header[6+n])<<(8*n);
            if(granule>=0)lastGranule=Math.max(lastGranule,granule);
            ended=(header[5]&4)!=0;
            if(lastGranule>=minimum*rate && lastGranule>0)
                return new Metadata(lastGranule/(double)rate,ended,encoded);
        }
        throw new IOException("Ogg page scan budget exceeded");
    }
    private static int littleInt(byte[] bytes,int offset){
        int value=0;for(int n=0;n<4;n++)value|=Byte.toUnsignedInt(bytes[offset+n])<<(8*n);return value;
    }
}
