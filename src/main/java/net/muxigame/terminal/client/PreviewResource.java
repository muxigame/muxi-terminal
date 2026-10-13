package net.muxigame.terminal.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Bounded encoded GIF transport for local, approved horse cover resources only. */
public final class PreviewResource {
    private static final int MAX_BYTES=1024*1024;
    private PreviewResource(){}
    public static void requireId(String namespace,String path){
        if(!namespace.equals("muxi_horse_racing")||!path.matches("textures/gui/umamusume_preview/[a-z0-9_/-]+\\.gif")||path.contains(".."))
            throw new IllegalArgumentException("Only local horse cover GIFs are supported");
    }
    public static String dataUri(InputStream input)throws IOException{
        byte[] data=input.readNBytes(MAX_BYTES+1);
        if(data.length>MAX_BYTES)throw new IllegalArgumentException("Animated preview exceeds 1 MiB");
        if(data.length<14)throw new IllegalArgumentException("Invalid GIF preview");
        String header=new String(data,0,6,StandardCharsets.US_ASCII);
        int width=(data[6]&255)|((data[7]&255)<<8),height=(data[8]&255)|((data[9]&255)<<8);
        if((!header.equals("GIF87a")&&!header.equals("GIF89a"))||width<1||height<1||width>256||height>256)
            throw new IllegalArgumentException("Animated previews are limited to 256x256 GIFs");
        return "data:image/gif;base64,"+Base64.getEncoder().encodeToString(data);
    }
}
