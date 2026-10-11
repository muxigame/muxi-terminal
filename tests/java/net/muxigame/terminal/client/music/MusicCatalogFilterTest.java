package net.muxigame.terminal.client.music;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MusicCatalogFilterTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static byte[] page(byte[] body,long granule,int flags){
        byte[] bytes=new byte[28+body.length];bytes[0]='O';bytes[1]='g';bytes[2]='g';bytes[3]='S';bytes[5]=(byte)flags;
        for(int n=0;n<8;n++)bytes[6+n]=(byte)(granule>>>(8*n));bytes[14]=1;bytes[26]=1;bytes[27]=(byte)body.length;
        System.arraycopy(body,0,bytes,28,body.length);return bytes;
    }
    private static byte[] ogg(int seconds,boolean ended)throws Exception{
        ByteArrayOutputStream file=new ByteArrayOutputStream();byte[] identification=new byte[30];
        identification[0]=1;System.arraycopy("vorbis".getBytes(),0,identification,1,6);identification[11]=1;
        int rate=48000;for(int n=0;n<4;n++)identification[12+n]=(byte)(rate>>>(8*n));
        file.write(page(identification,0,2));file.write(page(new byte[20],rate*(long)seconds,ended?4:0));return file.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        MusicCatalogFilter filter=new MusicCatalogFilter();AtomicInteger opens=new AtomicInteger();
        MusicCatalogFilter.Opener shortAudio=()->{opens.incrementAndGet();return new ByteArrayInputStream(oggSafe(7,true));};
        check(!filter.inspect("pack-generation-1:short",shortAudio,10).keep(),"7s retained at 10s");
        check(filter.inspect("pack-generation-1:short",shortAudio,10).cacheHit() && opens.get()==1,"same refresh reopened short audio");
        check(filter.inspect("pack-generation-1:short",shortAudio,5).keep() && opens.get()==1,"exact duration couldn't reuse lower threshold");
        MusicCatalogFilter.Opener longAudio=()->new ByteArrayInputStream(oggSafe(12,false));
        check(filter.inspect("long",longAudio,10).keep(),"proven 12s prefix wasn't retained");
        check(filter.inspect("long",longAudio,5).cacheHit(),"lower threshold reopened known-long prefix");
        check(!filter.inspect("long",longAudio,20).cacheHit(),"raised threshold reused insufficient lower bound");
        check(filter.inspect("unknown",()->new ByteArrayInputStream(new byte[]{1,2}),10).keep(),"unknown duration incorrectly removed music");
        check(MusicCatalogFilter.musicPath("sounds/music/game/ambient_song.ogg"),"title blacklisted");
        check(!MusicCatalogFilter.musicPath("sounds/music/ambient/wind.ogg"),"long ambient category retained");
        check(!MusicCatalogFilter.musicPath("sounds/music/sfx/explosion.ogg"),"long SFX category retained");
        check(!MusicCatalogFilter.musicPath("sounds/ambient/cave/cave1.ogg"),"effect entered music catalog");
        filter.clear();check(!filter.inspect("pack-generation-1:short",shortAudio,10).cacheHit() && opens.get()==2,"resource reload didn't invalidate metadata");
        if(args.length>0){
            List<String> inputs=Files.readAllLines(Path.of(args[0]));Map<String,java.util.zip.ZipFile> jars=new HashMap<>();
            try {
                long began=System.nanoTime(),bytes=0;int files=0,removed=0;
                for(String line:inputs){
                    String[] parts=line.split("\t");MusicCatalogFilter.Opener opener;
                    if(parts[0].equals("file"))opener=()->Files.newInputStream(Path.of(parts[1]));
                    else {
                        var jar=jars.get(parts[1]);if(jar==null){jar=new java.util.zip.ZipFile(parts[1]);jars.put(parts[1],jar);}
                        var opened=jar;opener=()->opened.getInputStream(opened.getEntry(parts[2]));
                    }
                    var result=filter.inspect(line,opener,10);
                    bytes+=result.metadata().encodedBytes();files++;if(!result.keep())removed++;
                }
                double coldMs=(System.nanoTime()-began)/1_000_000.0;began=System.nanoTime();int hits=0;
                for(String line:inputs)if(filter.inspect(line,()->{throw new AssertionError("cache reopened real file");},10).cacheHit())hits++;
                System.out.printf(Locale.ROOT,"{\"checks\":%d,\"realFiles\":%d,\"removedShort\":%d,\"encodedPrefixBytes\":%d,\"coldMetadataMs\":%.3f,\"cacheHits\":%d,\"warmMetadataMs\":%.3f,\"nativeMcTested\":false}%n",checks,files,removed,bytes,coldMs,hits,(System.nanoTime()-began)/1_000_000.0);
            }finally{for(var jar:jars.values())jar.close();}
        }else System.out.println("{\"checks\":"+checks+"}");
    }
    private static byte[] oggSafe(int seconds,boolean ended){try{return ogg(seconds,ended);}catch(Exception e){throw new RuntimeException(e);}}
}
