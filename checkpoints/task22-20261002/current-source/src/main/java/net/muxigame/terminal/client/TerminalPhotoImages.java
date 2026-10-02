package net.muxigame.terminal.client;

import com.mojang.blaze3d.platform.NativeImage;

/** Shared transient image resizing. The photo store remains the only disk source. */
public final class TerminalPhotoImages {
    private TerminalPhotoImages(){}
    static String data(TerminalPhotoStore store,String id,boolean recycled,int maxWidth,int maxHeight) throws Exception {
        byte[] png=recycled?store.readRecycled(id):store.read(id);
        if(png.length<24 || png[12]!=73 || png[13]!=72 || png[14]!=68 || png[15]!=82)throw new IllegalArgumentException("Invalid photo");
        var header=java.nio.ByteBuffer.wrap(png,16,8);int width=header.getInt(),height=header.getInt();
        if(width<1 || height<1 || (long)width*height>16_777_216L)throw new IllegalArgumentException("Photo too large");
        double scale=Math.min(1d,Math.min(maxWidth/(double)width,maxHeight/(double)height));
        int w=Math.max(1,(int)(width*scale)),h=Math.max(1,(int)(height*scale));
        try(var photo=NativeImage.read(png);var small=new NativeImage(w,h,false)){
            photo.resizeSubRectTo(0,0,photo.getWidth(),photo.getHeight(),small);return TerminalCamera.data(small.asByteArray());
        }
    }
}
