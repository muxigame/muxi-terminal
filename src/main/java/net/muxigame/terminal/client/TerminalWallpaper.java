package net.muxigame.terminal.client;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** No URLs, uploads, source names or paths leave this local codec/store. */
public final class TerminalWallpaper {
    public static final int MAX_BYTES=12*1024*1024, MAX_EDGE=8192;
    public static final long MAX_PIXELS=16_000_000;
    private TerminalWallpaper() {}
    public static byte[] normalize(Path source) throws IOException {
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS) || Files.size(source)>MAX_BYTES)
            throw new IOException("请选择不超过 12 MiB 的 PNG 或 JPEG 图片");
        byte[] bytes;
        try(var in=Files.newInputStream(source)){bytes=in.readNBytes(MAX_BYTES+1);}
        if(bytes.length>MAX_BYTES)throw new IOException("图片文件过大");
        try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new IOException("图片格式无效，仅支持 PNG / JPEG");
            var reader=readers.next();
            try {
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))throw new IOException("仅支持 PNG / JPEG");
                reader.setInput(stream,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1 || h<1 || w>MAX_EDGE || h>MAX_EDGE || (long)w*h>MAX_PIXELS)
                    throw new IOException("图片尺寸过大：最长边 8192，最多 1600 万像素");
                var image=reader.read(0);
                try {
                    double scale=Math.min(1,2048.0/Math.max(w,h));
                    var clean=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_RGB);
                    var g=clean.createGraphics();
                    try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,clean.getWidth(),clean.getHeight(),null);}
                    finally{g.dispose();}
                    try(var out=new ByteArrayOutputStream()){
                        ImageIO.write(clean,"png",out);return out.toByteArray();
                    }finally{clean.flush();}
                }finally{image.flush();}
            }finally{reader.dispose();}
        }
    }
    public static void atomicWrite(Path target,byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        if(Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent()))throw new IOException("本地设置目录无效");
        Path temp=Files.createTempFile(target.getParent(),"settings-",".tmp");
        try {
            Files.write(temp,bytes);
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    public static String data(byte[] png){return "data:image/png;base64,"+Base64.getEncoder().encodeToString(png);}
}
