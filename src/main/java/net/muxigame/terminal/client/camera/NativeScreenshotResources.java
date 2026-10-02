package net.muxigame.terminal.client.camera;

import com.mojang.blaze3d.platform.NativeImage;

/** A render-thread lease transfers one native allocation to the existing screenshot IO task. */
public final class NativeScreenshotResources {
    private static final ThreadLocal<Lease> CURRENT=new ThreadLocal<>();
    private NativeScreenshotResources(){}
    public static final class Lease {
        public NativeImage image;
        public boolean handedOff;
    }
    public static Lease current(){return CURRENT.get();}
    public static Lease begin(){Lease lease=new Lease();CURRENT.set(lease);return lease;}
    public static void end(Lease lease,Lease previous){
        try{if(lease.image!=null && !lease.handedOff)lease.image.close();}
        finally{lease.image=null;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
}
