package net.muxigame.terminal.client;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Transient bounded PNG/JPEG/BMP/GIF decode; no NativeImage, texture, disk cache or URL ownership. */
public final class TerminalPhotoImages {
    private TerminalPhotoImages(){}
    static String data(TerminalPhotoStore store,String id,boolean recycled,int maxWidth,int maxHeight) throws Exception {
        byte[] bytes=recycled?store.readRecycled(id):store.read(id);
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Invalid photo");
            var reader=readers.next();BufferedImage photo=null,small=null;
            try{
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || (long)width*height>16_777_216L)throw new IllegalArgumentException("Photo too large");
                double scale=Math.min(1d,Math.min(maxWidth/(double)width,maxHeight/(double)height));
                int w=Math.max(1,(int)(width*scale)),h=Math.max(1,(int)(height*scale));
                var parameters=reader.getDefaultReadParam();
                int sample=Math.max(1,(int)Math.floor(1d/scale));parameters.setSourceSubsampling(sample,sample,0,0);
                photo=reader.read(0,parameters);small=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
                var graphics=small.createGraphics();
                try{graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(photo,0,0,w,h,null);}finally{graphics.dispose();}
                try(var png=new ByteArrayOutputStream()){
                    if(!ImageIO.write(small,"png",png))throw new IllegalArgumentException("Photo encoding unavailable");return TerminalCamera.data(png.toByteArray());
                }
            }finally{reader.dispose();if(photo!=null)photo.flush();if(small!=null)small.flush();}
        }
    }
}
